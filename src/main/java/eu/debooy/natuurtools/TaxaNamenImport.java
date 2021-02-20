/**
 * Copyright (c) 2020 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.natuurtools;

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosConstants;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.components.Message;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.TaxonDto;
import static eu.debooy.natuur.domain.TaxonDto.PAR_LATIJNSENAAM;
import static eu.debooy.natuur.domain.TaxonDto.QRY_LATIJNSENAAM;
import eu.debooy.natuur.domain.TaxonnaamDto;
import eu.debooy.natuur.validator.TaxonValidator;
import eu.debooy.natuur.validator.TaxonnaamValidator;
import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * @author Marco de Booij
 */
public class TaxaNamenImport extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  String  TXT_2SPATIES = "  ";
  protected static final  String  TXT_4SPATIES = "    ";
  protected static final  String  TXT_6SPATIES = "      ";

  private static  boolean       aanmaak   = false;
  private static  boolean       readonly  = false;
  private static  EntityManager em;

  private TaxaNamenImport() {}

  public static void execute(String[] args) {
    JsonBestand jsonBestand = null;
    Properties  props       = new Properties();

    Banner.printDoosBanner(resourceBundle.getString("banner.taxanamenimport"));

    if (!setParameters(args)) {
      return;
    }

    String  password  =
        DoosUtils.getWachtwoord(MessageFormat.format(
            resourceBundle.getString(NatuurTools.LBL_WACHTWOORD),
            parameters.get(NatuurTools.PAR_DBUSER),
            parameters.get(NatuurTools.PAR_DBURL).split("/")[1]));

    if (DoosConstants.WAAR
                     .equalsIgnoreCase(parameters.get(PAR_READONLY))) {
      readonly  = true;
    } else {
      DoosUtils.naarScherm();
      DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_WIJZIGEN));
      if (DoosConstants.WAAR
                       .equalsIgnoreCase(parameters
                                            .get(NatuurTools.PAR_AANMAAK))) {
        aanmaak = true;
        DoosUtils.naarScherm(resourceBundle
                                .getString(NatuurTools.MSG_AANMAKEN));
      }
      DoosUtils.naarScherm();
    }

    String[]      talen       = parameters.get(NatuurTools.PAR_TALEN)
                                          .split(",");
    Arrays.sort(talen);

    props.put("openjpa.ConnectionURL",
              "jdbc:postgresql://" + parameters.get(NatuurTools.PAR_DBURL));
    props.put("openjpa.ConnectionUserName",
              parameters.get(NatuurTools.PAR_DBUSER));
    props.put("openjpa.ConnectionPassword", password);
    em  = Persistence.createEntityManagerFactory("natuur", props)
                     .createEntityManager();

    try {
      jsonBestand  =
          new JsonBestand.Builder()
                         .setBestand(parameters.get(PAR_INVOERDIR)
                                    + parameters.get(PAR_JSONBESTAND)
                                    + EXT_JSON)
                         .setCharset(parameters.get(PAR_CHARSETIN))
                         .build();
      verwerkJson(jsonBestand.read(), talen);
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    } finally {
      if (null != jsonBestand) {
        try {
          jsonBestand.close();
        } catch (BestandException e) {
          DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
        }
      }
    }

    if (null != em) {
      em.close();
    }

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
    DoosUtils.naarScherm();
  }

  public static void addTaxon(TaxonDto taxon) {
    if (readonly || !aanmaak) {
      return;
    }

    List<Message>  fouten  = TaxonValidator.valideer(taxon);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      em.persist(taxon);
      em.getTransaction().commit();
    } else {
      printMessages(fouten);
    }
  }

  public static void addTaxonnaam(TaxonnaamDto taxonnaam) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonnaamValidator.valideer(taxonnaam);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      em.persist(taxonnaam);
      em.getTransaction().commit();
    } else {
      printMessages(fouten);
    }
  }

  public static void controleerTaxonnamen(TaxonDto taxon, JSONObject taxonnamen,
                                          String[] talen) {
    for (String taal : talen) {
      if (taxonnamen.containsKey(taal)) {
        TaxonnaamDto  taxonnaamDto;
        if (taxon.hasTaxonnaam(taal)) {
          taxonnaamDto  = taxon.getTaxonnaam(taal);
          if (!taxonnaamDto.getNaam()
                           .equals(taxonnamen.get(taal))) {
            DoosUtils.naarScherm(TXT_6SPATIES
                + MessageFormat.format(
                      resourceBundle
                          .getString(NatuurTools.MSG_VERSCHIL),
                      taal, taxonnamen.get(taal),
                      taxonnaamDto.getNaam()));
            taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
            setTaxonnaam(taxonnaamDto);
          }
        } else {
          DoosUtils.naarScherm(TXT_6SPATIES
                + MessageFormat.format(
                      resourceBundle
                          .getString(NatuurTools.MSG_NIEUW),
                      taal, taxonnamen.get(taal)));
          taxonnaamDto  = new TaxonnaamDto();
          taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
          taxonnaamDto.setTaal(taal);
          taxonnaamDto.setTaxonId(taxon.getTaxonId());
          addTaxonnaam(taxonnaamDto);
        }
      }
    }
    for (TaxonnaamDto dto : taxon.getTaxonnamen()) {
      if (!taxonnamen.containsKey(dto.getTaal())) {
        DoosUtils.foutNaarScherm(TXT_6SPATIES
            + MessageFormat.format(
                  resourceBundle
                        .getString(NatuurTools.MSG_ONBEKEND),
                  dto.getTaal(), dto.getNaam()));
      }
    }
  }

  public static TaxonDto getTaxon(String latijnsenaam, Long parentId,
                                  Integer volgnummer, String rang) {
    em.getTransaction().begin();
    Query query = em.createNamedQuery(QRY_LATIJNSENAAM);
    em.getTransaction().commit();
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
      if (aanmaak) {
        resultaat.setLatijnsenaam(latijnsenaam);
        resultaat.setParentId(parentId);
        resultaat.setRang(rang);
        resultaat.setVolgnummer(volgnummer);
        addTaxon(resultaat);
      }
    }

    return resultaat;
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar TaxaNamenImport ["
                          + getMelding(LBL_OPTIE)
                          + "] --jsonbestand=<"
                          + resourceBundle.getString("label.jsonbestand") + ">"
                          + " --dburl=<"
                          + resourceBundle.getString("label.dburl") + ">"
                          + " --dbuser=<"
                          + resourceBundle.getString("label.dbuser") + ">"
                          + " --talen=<"
                          + resourceBundle.getString("label.talen") + ">",
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_AANMAAK, 12),
                         resourceBundle.getString("help.aanmaak"), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETIN, 12),
        MessageFormat.format(getMelding(HLP_CHARSETIN),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBURL, 12),
                         resourceBundle.getString("help.dburl"), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBUSER, 12),
                         resourceBundle.getString("help.dbuser"), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_INVOERDIR, 12),
                         getMelding(HLP_INVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_JSONBESTAND, 12),
                         resourceBundle.getString("help.jsonbestand"), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_READONLY, 12),
                         resourceBundle.getString("help.readonly"), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 12),
                         resourceBundle.getString("help.include.talen"), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             PAR_JSONBESTAND + ", "
                              + NatuurTools.PAR_DBURL + ", "
                              + NatuurTools.PAR_DBUSER,
                             NatuurTools.PAR_TALEN), 80);
    DoosUtils.naarScherm();
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.getMessage())));
  }

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {NatuurTools.PAR_AANMAAK,
                                          PAR_CHARSETIN,
                                          NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          PAR_INVOERDIR,
                                          PAR_JSONBESTAND,
                                          PAR_READONLY,
                                          NatuurTools.PAR_TALEN});
    arguments.setVerplicht(new String[] {PAR_JSONBESTAND,
                                         NatuurTools.PAR_TALEN});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    setParameter(arguments, NatuurTools.PAR_AANMAAK, DoosConstants.ONWAAR);
    setParameter(arguments, PAR_CHARSETIN, Charset.defaultCharset().name());
    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, PAR_JSONBESTAND, EXT_JSON);
    setParameter(arguments, PAR_READONLY, DoosConstants.ONWAAR);
    NatuurTools.setTalenParameter(arguments, parameters);

    if (DoosUtils.nullToEmpty(parameters.get(PAR_JSONBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              getMelding(ERR_BEVATDIRECTORY), PAR_JSONBESTAND));
    }

    if (fouten.isEmpty()) {
      return true;
    }

    help();
    printFouten(fouten);

    return false;
  }

  public static void setTaxon(TaxonDto taxon) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonValidator.valideer(taxon);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonDto  updated = em.merge(taxon);
      em.persist(updated);
      em.getTransaction().commit();
    } else {
      printMessages(fouten);
    }
  }

  public static void setTaxonnaam(TaxonnaamDto taxonnaam) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonnaamValidator.valideer(taxonnaam);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonnaamDto  updated = em.merge(taxonnaam);
      em.persist(updated);
      em.getTransaction().commit();
    } else {
      printMessages(fouten);
    }
  }

  private static void verwerkFamilies(JSONArray families, Long ordeId,
                                      String[] talen) {
    families.forEach(familie -> {
      String    familienaam =
              ((JSONObject) familie).get(NatuurTools.KEY_FAMILIE)
                      .toString();
      Integer   id          =
              Integer.valueOf(((JSONObject) familie).get(NatuurTools.KEY_ID)
                      .toString());
      TaxonDto  taxon       = getTaxon(familienaam, ordeId, id, "fa");
      Long      familieId   = taxon.getTaxonId();
      if (familienaam.equals(
              DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
        DoosUtils.naarScherm(TXT_2SPATIES + MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_FAMILIE),
                familienaam, taxon.getVolgnummer(), id));
        if (!Objects.equals(taxon.getVolgnummer(), id)) {
          taxon.setVolgnummer(id);
          setTaxon(taxon);
        }
        Object  soorten =
                ((JSONObject) familie).get(NatuurTools.KEY_SOORTEN);
        verwerkSoorten((JSONArray) soorten, familieId, talen);
      }
    });
  }

  private static void verwerkJson(JSONObject json, String[] talen) {
    Integer   id;
    Long      ordeId;
    TaxonDto  taxon;
    Long      vogelId;

    vogelId = getTaxon("Aves", 0L, 0, "kl").getTaxonId();

    // Verwerk orde per orde
    for (Object orde : (JSONArray) json.get("taxa")) {
      String  ordenaam  =
          StringUtils.capitalize(((JSONObject) orde).get(NatuurTools.KEY_ORDE)
                                                    .toString()
                                                    .toLowerCase());
      id  = Integer.valueOf(((JSONObject) orde).get(NatuurTools.KEY_ID)
                                               .toString());
      taxon   = getTaxon(ordenaam, vogelId, id, "or");
      ordeId  = taxon.getTaxonId();
      if (ordenaam.equals(DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
        DoosUtils.naarScherm(MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_ORDE),
            ordenaam, taxon.getVolgnummer(), id));
        if (!Objects.equals(taxon.getVolgnummer(), id)) {
          taxon.setVolgnummer(id);
          setTaxon(taxon);
        }
        Object  families  = ((JSONObject) orde).get(NatuurTools.KEY_FAMILIES);
        verwerkFamilies((JSONArray) families, ordeId, talen);
      }
    }
  }

  private static void verwerkSoorten(JSONArray soorten, Long familieId,
                                     String[] talen) {
    soorten.forEach(soort -> {
      String  soortnaam =
              ((JSONObject) soort).get(NatuurTools.KEY_LATIJN).toString();
      Integer id  = Integer.valueOf(((JSONObject) soort).get(NatuurTools.KEY_ID)
              .toString());
      TaxonDto  taxon = getTaxon(soortnaam, familieId, id, "so");
      if (soortnaam.equals(
              DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
        DoosUtils.naarScherm(TXT_4SPATIES + MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_SOORT),
                soortnaam, taxon.getVolgnummer(), id));
        if (!Objects.equals(taxon.getVolgnummer(), id)) {
          taxon.setVolgnummer(id);
          setTaxon(taxon);
        }
        Object  jObject  =
                ((JSONObject) soort).get(NatuurTools.KEY_NAMEN);
        controleerTaxonnamen(taxon, (JSONObject) jObject, talen);
      }
    });
  }
}