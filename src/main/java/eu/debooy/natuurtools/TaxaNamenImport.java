/**
 * Copyright 2020 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
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

  private static  boolean       readonly  = false;
  private static  EntityManager em;

  public static void execute(String[] args) {
    Integer       id          = null;
    JSONObject    json        = null;
    JsonBestand   jsonBestand = null;
    Properties    props       = new Properties();
    TaxonDto      taxon       = null;

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
                     .equalsIgnoreCase(
                          parameters.get(NatuurTools.PAR_READONLY))) {
      readonly = true;
    } else {
      DoosUtils.naarScherm();
      DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_WIJZIGEN));
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
                                    + File.separator +
                                    parameters.get(NatuurTools.PAR_JSONBESTAND)
                                    + EXT_JSON)
                         .setCharset(parameters.get(PAR_CHARSETIN))
                         .build();
      json = jsonBestand.read();
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    // Verwerk orde per orde
    for (Object orde : (JSONArray) json.get("taxa")) {
      String  ordenaam  =
          StringUtils.capitalize(((JSONObject) orde).get(NatuurTools.KEY_ORDE)
                                                    .toString()
                                                    .toLowerCase());
      taxon = getTaxon(ordenaam);
      if (ordenaam.equals(DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
        id  = Integer.valueOf(((JSONObject) orde).get(NatuurTools.KEY_ID)
                                                 .toString());
        DoosUtils.naarScherm(MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_ORDE),
            ordenaam, taxon.getVolgnummer(), id));
        if (!Objects.equals(taxon.getVolgnummer(), id)) {
          taxon.setVolgnummer(id);
          setTaxon(taxon);
        }
        Object  families  = ((JSONObject) orde).get(NatuurTools.KEY_FAMILIES);
        for (Object familie : (JSONArray) families) {
          String  familienaam =
                  ((JSONObject) familie).get(NatuurTools.KEY_FAMILIE)
                                        .toString();
          taxon = getTaxon(familienaam);
          if (familienaam.equals(
                  DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
            id  = Integer.valueOf(((JSONObject) familie).get(NatuurTools.KEY_ID)
                                                        .toString());
            DoosUtils.naarScherm("  " + MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_FAMILIE),
                familienaam, taxon.getVolgnummer(), id));
            if (!Objects.equals(taxon.getVolgnummer(), id)) {
              taxon.setVolgnummer(id);
              setTaxon(taxon);
            }
            Object  soorten =
                ((JSONObject) familie).get(NatuurTools.KEY_SOORTEN);
            for (Object soort : (JSONArray) soorten) {
              String  soortnaam =
                  ((JSONObject) soort).get(NatuurTools.KEY_LATIJN).toString();
              taxon = getTaxon(soortnaam);
              if (soortnaam.equals(
                      DoosUtils.nullToEmpty(taxon.getLatijnsenaam()))) {
                id  =
                    Integer.valueOf(((JSONObject) soort).get(NatuurTools.KEY_ID)
                                                        .toString());
                DoosUtils.naarScherm("    " + MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_SOORT),
                    soortnaam, taxon.getVolgnummer(), id));
                if (!Objects.equals(taxon.getVolgnummer(), id)) {
                  taxon.setVolgnummer(id);
                  setTaxon(taxon);
                }
                Object  jObject  =
                    ((JSONObject) soort).get(NatuurTools.KEY_NAMEN);
                JSONObject  taxonnamen  = (JSONObject) jObject;
                controleerTaxonnamen(taxon, taxonnamen, talen);
              }
            }
          }
        }
      }
    }

    if (null != jsonBestand) {
      try {
        jsonBestand.close();
      } catch (BestandException e) {
        DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
      }
    }

    if (null != em) {
      em.close();
    }

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(resourceBundle.getString(MSG_KLAAR));
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
      printFouten(fouten);
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
            DoosUtils.naarScherm("      "
                + MessageFormat.format(
                      resourceBundle
                          .getString(NatuurTools.MSG_VERSCHIL),
                      taal, taxonnamen.get(taal),
                      taxonnaamDto.getNaam()));
            taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
            setTaxonnaam(taxonnaamDto);
          }
        } else {
          DoosUtils.naarScherm("      "
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
        DoosUtils.foutNaarScherm("      "
            + MessageFormat.format(
                  resourceBundle
                        .getString(NatuurTools.MSG_ONBEKEND),
                  dto.getTaal(), dto.getNaam()));
      }
    }
  }
  public static TaxonDto getTaxon(String latijnsenaam) {
    em.getTransaction().begin();
    Query query = em.createNamedQuery(QRY_LATIJNSENAAM);
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
    }
    em.getTransaction().commit();

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
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_READONLY, 12),
                         resourceBundle.getString("help.readonly"), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 12),
                         resourceBundle.getString("help.include.talen"), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             NatuurTools.PAR_JSONBESTAND + ", "
                                 + NatuurTools.PAR_DBURL + ", "
                                 + NatuurTools.PAR_DBUSER,
                             NatuurTools.PAR_TALEN), 80);
    DoosUtils.naarScherm();
  }

  protected static void printFouten(List<Message> fouten) {
    fouten.forEach(fout -> {
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.getMessage()));
    });
  }

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<String>();

    arguments.setParameters(new String[] {PAR_CHARSETIN,
                                          NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          PAR_INVOERDIR,
                                          NatuurTools.PAR_JSONBESTAND,
                                          NatuurTools.PAR_READONLY,
                                          NatuurTools.PAR_TALEN});
    arguments.setVerplicht(new String[] {NatuurTools.PAR_JSONBESTAND,
                                         NatuurTools.PAR_TALEN});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    setParameter(arguments, PAR_CHARSETIN,
                 Charset.defaultCharset().name());
    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, NatuurTools.PAR_JSONBESTAND, EXT_JSON);
    setParameter(arguments, NatuurTools.PAR_READONLY, DoosConstants.ONWAAR);
    NatuurTools.setTalenParameter(arguments, parameters);

    if (parameters.get(NatuurTools.PAR_JSONBESTAND).contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              resourceBundle.getString(ERR_BEVATDIRECTORY),
                                       NatuurTools.PAR_JSONBESTAND));
    }

    return isFoutloos(fouten);
  }

  public static void setTaxon(TaxonDto taxon) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonValidator.valideer(taxon);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonDto  updated = (TaxonDto) em.merge(taxon);
      em.persist(updated);
      em.getTransaction().commit();
    } else {
      printFouten(fouten);
    }
  }

  public static void setTaxonnaam(TaxonnaamDto taxonnaam) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonnaamValidator.valideer(taxonnaam);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonnaamDto  updated = (TaxonnaamDto) em.merge(taxonnaam);
      em.persist(updated);
      em.getTransaction().commit();
    } else {
      printFouten(fouten);
    }
  }
}