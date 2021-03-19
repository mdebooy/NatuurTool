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
import eu.debooy.natuur.domain.RangDto;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class TaxaImport extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  Long  ONBEKEND  = -1L;

  private static final  Map<String, String>   prefix  = new HashMap<>();
  private static final  List<String>          rangen  = new ArrayList<>();
  private static final  List<String>          talen   = new ArrayList<>();
  private static final  Map<String, Integer>  totalen = new HashMap<>();

  private static  boolean       aanmaak       = false;
  private static  EntityManager em;
  private static  boolean       hernummer     = false;
  private static  boolean       readonly      = false;
  private static  boolean       skipstructuur = false;

  private TaxaImport() {}

  public static void execute(String[] args) {
    JsonBestand jsonBestand   = null;
    String      latijnsenaam  = "?";

    Banner.printDoosBanner(resourceBundle.getString("banner.taxaimport"));

    if (!setParameters(args)) {
      return;
    }

    setSwitches();

    if (parameters.containsKey(NatuurTools.PAR_TALEN)) {
      talen.addAll(Arrays.asList(parameters.get(NatuurTools.PAR_TALEN)
                                           .split(",")));
      Collections.sort(talen);
    }

    if (parameters.containsKey(NatuurTools.PAR_WACHTWOORD)) {
      em  = NatuurTools.getEntityManager(
                parameters.get(NatuurTools.PAR_DBUSER),
                parameters.get(NatuurTools.PAR_DBURL),
                parameters.get(NatuurTools.PAR_WACHTWOORD));
    } else {
      em  = NatuurTools.getEntityManager(
                parameters.get(NatuurTools.PAR_DBUSER),
                parameters.get(NatuurTools.PAR_DBURL));
    }

    getRangen();

    try {
      jsonBestand       =
          new JsonBestand.Builder()
                         .setBestand(parameters.get(PAR_INVOERDIR)
                                    + parameters.get(PAR_JSONBESTAND)
                                    + EXT_JSON)
                         .setCharset(parameters.get(PAR_CHARSETIN))
                         .build();
      latijnsenaam      = jsonBestand.read().get(NatuurTools.KEY_LATIJN)
                                            .toString();
      String  rang      = jsonBestand.read().get(NatuurTools.KEY_RANG)
                                            .toString();
      Long    parentId  = getTaxon(latijnsenaam, 0L, 0, rang).getTaxonId();
      for (Object taxa :
              (JSONArray) jsonBestand.read().get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(parentId, (JSONObject) taxa);
      }
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
    DoosUtils.naarScherm(latijnsenaam);
    DoosUtils.naarScherm();
    rangen.forEach(rang -> {
      Integer volgnummer  = totalen.get(rang);
      if (volgnummer > 0) {
        DoosUtils.naarScherm(String.format("%6s: %,6d",
                rang, totalen.get(rang)));
      }
    });
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
    DoosUtils.naarScherm();
  }

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang) + 1);
  }

  private static void addTaxon(TaxonDto taxon) {
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

  private static void addTaxonnaam(TaxonnaamDto taxonnaam) {
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

  private static void controleerHierarchie(TaxonDto taxon, Long parentId,
                                           Integer volgnummer) {
    if (readonly || skipstructuur) {
      return;
    }

    StringBuilder verandering = new StringBuilder();
    if (!parentId.equals(taxon.getParentId())) {
      verandering.append("parentId: ").append(taxon.getParentId())
                 .append(" - > ").append(parentId).append(" ");
      taxon.setParentId(parentId);
    }
    if (!volgnummer.equals(taxon.getVolgnummer()) && hernummer) {
      verandering.append("volgnummer: ").append(taxon.getVolgnummer())
                 .append(" - > ").append(volgnummer);
      taxon.setVolgnummer(volgnummer);
    }
    if (verandering.length() > 0) {
      setTaxon(taxon);
      DoosUtils.naarScherm(MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_WIJZIGING),
                    prefix.get(taxon.getRang()) + "    ",
                    resourceBundle.getString(NatuurTools.MSG_HIERARCHIE),
                    verandering.toString().trim()));
    }
  }

  private static void controleerTaxonnamen(TaxonDto taxon,
                                           JSONObject taxonnamen) {
    for (Object key : taxonnamen.keySet()) {
      String  taal  = key.toString();
      if (isTaalValid(taal)
          && DoosUtils.isNotBlankOrNull(taxonnamen.get(taal))) {
        TaxonnaamDto  taxonnaamDto;
        if (taxon.hasTaxonnaam(taal)) {
          taxonnaamDto  = taxon.getTaxonnaam(taal);
          if (!taxonnaamDto.getNaam()
                           .equals(taxonnamen.get(taal))) {
            DoosUtils.naarScherm(
                MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_VERSCHIL),
                    prefix.get(taxon.getRang()) + "    ", taal,
                    taxonnamen.get(taal), taxonnaamDto.getNaam()));
            taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
            setTaxonnaam(taxonnaamDto);
          }
        } else {
          DoosUtils.naarScherm(
                MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_NIEUW),
                    prefix.get(taxon.getRang()) + "    ", taal,
                    taxonnamen.get(taal)));
          taxonnaamDto  = new TaxonnaamDto();
          taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
          taxonnaamDto.setTaal(taal);
          taxonnaamDto.setTaxonId(taxon.getTaxonId());
          addTaxonnaam(taxonnaamDto);
        }
      }
    }

    taxon.getTaxonnamen().forEach(dto -> {
      if (!taxonnamen.containsKey(dto.getTaal())) {
        DoosUtils.foutNaarScherm(
            MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_ONBEKEND),
                prefix.get(taxon.getRang()) + "   ", dto.getTaal(),
                dto.getNaam()));
      }
    });
  }

  private static void getRangen() {
    em.getTransaction().begin();
    List<RangDto> ranglijst =
        em.createQuery("select r from RangDto r order by r.niveau")
          .getResultList();
    em.getTransaction().commit();

    ranglijst.forEach(rang -> {
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue()));
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
  }

  private static TaxonDto getTaxon(String latijnsenaam, Long parentId,
                                   Integer volgnummer, String rang) {
    em.getTransaction().begin();
    Query query = em.createNamedQuery(QRY_LATIJNSENAAM);
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    em.getTransaction().commit();
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
      resultaat.setLatijnsenaam(latijnsenaam);
      resultaat.setRang(rang);
      resultaat.setVolgnummer(volgnummer);
      if (aanmaak) {
        resultaat.setParentId(parentId);
        addTaxon(resultaat);
      } else {
        resultaat.setParentId(ONBEKEND);
      }
    }

    return resultaat;
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar TaxaImport "
        + getMelding(LBL_OPTIE) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), PAR_JSONBESTAND,
              resourceBundle.getString(NatuurTools.LBL_JSONBESTAND)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBURL,
              resourceBundle.getString(NatuurTools.LBL_DBURL)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBUSER,
              resourceBundle.getString(NatuurTools.LBL_DBUSER)), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_AANMAAK, 14),
                         resourceBundle.getString(NatuurTools.HLP_AANMAAK), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETIN, 14),
        MessageFormat.format(getMelding(HLP_CHARSETIN),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBURL, 14),
                         resourceBundle.getString(NatuurTools.HLP_DBURL), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBUSER, 14),
                         resourceBundle.getString(NatuurTools.HLP_DBUSER), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_HERNUMMER, 14),
                         resourceBundle.getString(NatuurTools.HLP_HERNUMMER),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_INVOERDIR, 14),
                         getMelding(HLP_INVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_JSONBESTAND, 14),
                         resourceBundle.getString(NatuurTools.HLP_JSONBESTAND),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_READONLY, 14),
                         resourceBundle.getString(NatuurTools.HLP_READONLY),
                         80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_SKIPSTRUCTUUR, 14),
                         resourceBundle
                             .getString(NatuurTools.HLP_SKIPSTRUCTUUR), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 14),
                         resourceBundle.getString(NatuurTools.HLP_INCLUDETALEN),
                         80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_WACHTWOORD, 12),
                         resourceBundle.getString(NatuurTools.HLP_WACHTWOORD),
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             PAR_JSONBESTAND + ", " + NatuurTools.PAR_DBURL,
                             NatuurTools.PAR_DBUSER), 80);
    DoosUtils.naarScherm();
  }

  protected static boolean isTaalValid(String taal) {
    return talen.isEmpty() || talen.contains(taal);
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.toString())));
  }

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {NatuurTools.PAR_AANMAAK,
                                          PAR_CHARSETIN,
                                          NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          NatuurTools.PAR_HERNUMMER,
                                          PAR_INVOERDIR,
                                          PAR_JSONBESTAND,
                                          PAR_READONLY,
                                          NatuurTools.PAR_SKIPSTRUCTUUR,
                                          NatuurTools.PAR_TALEN,
                                          NatuurTools.PAR_WACHTWOORD});
    arguments.setVerplicht(new String[] {PAR_JSONBESTAND,
                                         NatuurTools.PAR_DBURL,
                                         NatuurTools.PAR_DBUSER});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    parameters  = new HashMap<>();

    setParameter(arguments, NatuurTools.PAR_AANMAAK, DoosConstants.ONWAAR);
    setParameter(arguments, PAR_CHARSETIN, Charset.defaultCharset().name());
    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setParameter(arguments, NatuurTools.PAR_HERNUMMER, DoosConstants.ONWAAR);
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, PAR_JSONBESTAND, EXT_JSON);
    setParameter(arguments, PAR_READONLY, DoosConstants.ONWAAR);
    setParameter(arguments, NatuurTools.PAR_SKIPSTRUCTUUR,
                 DoosConstants.ONWAAR);
    NatuurTools.setTalenParameter(arguments, parameters);
    setParameter(arguments, NatuurTools.PAR_WACHTWOORD);

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

  private static void setSwitches() {
    if (DoosUtils.isTrue(parameters.get(PAR_READONLY))) {
      readonly        = true;
    } else {
      DoosUtils.naarScherm();
      DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_WIJZIGEN));
      if (DoosUtils.isTrue(parameters.get(NatuurTools.PAR_SKIPSTRUCTUUR))) {
        skipstructuur = true;
        DoosUtils.naarScherm(resourceBundle
                                .getString(NatuurTools.MSG_SKIPSTRUCTUUR));
      } else {
        if (DoosUtils.isTrue(parameters.get(NatuurTools.PAR_AANMAAK))) {
          aanmaak       = true;
          DoosUtils.naarScherm(resourceBundle
                                  .getString(NatuurTools.MSG_AANMAKEN));
        }
        if (DoosUtils.isTrue(parameters.get(NatuurTools.PAR_HERNUMMER))) {
          hernummer     = true;
          DoosUtils.naarScherm(resourceBundle
                                  .getString(NatuurTools.MSG_HERNUMMER));
        }
      }
      DoosUtils.naarScherm();
    }
  }

  private static void setTaxon(TaxonDto taxon) {
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

  private static void setTaxonnaam(TaxonnaamDto taxonnaam) {
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

  private static void verwerkRang(Long parentId, JSONObject json) {
    String  latijnsenaam  = json.get(NatuurTools.KEY_LATIJN).toString();
    String  rang          = json.get(NatuurTools.KEY_RANG).toString();
    Integer seq           =
        Integer.valueOf(json.get(NatuurTools.KEY_SEQ).toString());

    DoosUtils.naarScherm(String.format("%s%-3s %s",
                                       prefix.get(rang), rang, latijnsenaam));

    TaxonDto  taxon = getTaxon(latijnsenaam, parentId, seq, rang);
    addRang(rang);
    controleerHierarchie(taxon, parentId, seq);

    if (null == taxon.getTaxonId()) {
      return;
    }

    if (json.containsKey(NatuurTools.KEY_NAMEN)) {
      controleerTaxonnamen(taxon, (JSONObject) json.get(NatuurTools.KEY_NAMEN));
    }
    if (json.containsKey(NatuurTools.KEY_SUBRANGEN)) {
      for (Object subrang : (JSONArray) json.get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(taxon.getTaxonId(), (JSONObject) subrang);
      }
    }
  }
}