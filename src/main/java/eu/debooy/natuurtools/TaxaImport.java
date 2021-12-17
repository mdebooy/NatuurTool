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

import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.BestandConstants;
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

  private static  boolean       aanmaak   = false;
  private static  boolean       behoud    = false;
  private static  EntityManager em;
  private static  boolean       hernummer = false;
  private static  boolean       readonly  = false;

  protected TaxaImport() {}

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_TAXAIMPORT)
                           .setClassloader(TaxaImport.class.getClassLoader())
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    if (paramBundle.containsParameter(NatuurTools.PAR_TALEN)) {
      talen.addAll(Arrays.asList(paramBundle.getString(NatuurTools.PAR_TALEN)
                                            .split(",")));
      Collections.sort(talen);
    }

    em  = NatuurTools.getEntityManager(
              paramBundle.getString(NatuurTools.PAR_DBUSER),
              paramBundle.getString(NatuurTools.PAR_DBURL),
              paramBundle.getString(NatuurTools.PAR_WACHTWOORD));

    getRangen();
    setSwitches();

    var latijnsenaam  = "?";

    try (var jsonBestand   =
          new JsonBestand.Builder()
                         .setBestand(
                             paramBundle.getBestand(NatuurTools.PAR_JSON,
                                                    BestandConstants.EXT_JSON))
                         .setCharset(paramBundle.getString(PAR_CHARSETIN))
                         .build()) {
      latijnsenaam    = jsonBestand.read().get(NatuurTools.KEY_LATIJN)
                                          .toString();
      var   rang      = jsonBestand.read().get(NatuurTools.KEY_RANG)
                                          .toString();
      Long  parentId  = getTaxon(latijnsenaam, 0L, 0, rang).getTaxonId();
      for (Object taxa :
              (JSONArray) jsonBestand.read().get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(parentId, (JSONObject) taxa);
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    em.close();

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
    if (readonly) {
      return;
    }

    var verandering = new StringBuilder();
    if (!behoud && !parentId.equals(taxon.getParentId())) {
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
    for (var key : taxonnamen.keySet()) {
      var taal  = key.toString();
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
    List<RangDto> ranglijst =
        em.createQuery(NatuurTools.QRY_RANG).getResultList();

    ranglijst.forEach(rang -> {
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue()));
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
  }

  private static TaxonDto getTaxon(String latijnsenaam, Long parentId,
                                   Integer volgnummer, String rang) {
    var query = em.createNamedQuery(QRY_LATIJNSENAAM);
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
      addRang(rang);
      printTaxon(rang, latijnsenaam);
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
      resultaat.setLatijnsenaam(latijnsenaam);
      resultaat.setRang(rang);
      resultaat.setVolgnummer(volgnummer);
      if (aanmaak) {
        resultaat.setParentId(parentId);
        addTaxon(resultaat);
        addRang(rang);
        printTaxon(rang, latijnsenaam);
      } else {
        resultaat.setParentId(ONBEKEND);
      }
    }

    return resultaat;
  }

  protected static boolean isTaalValid(String taal) {
    return talen.isEmpty() || talen.contains(taal);
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.toString())));
  }

  protected static void printTaxon(String rang, String latijnsenaam) {
    DoosUtils.naarScherm(String.format("%s%-3s %s",
                                       prefix.get(rang), rang, latijnsenaam));
  }

  private static void setSwitches() {
    if (Boolean.TRUE.equals(paramBundle.getBoolean(PAR_READONLY))) {
      readonly  = true;
      return;
    }

    aanmaak   = paramBundle.getBoolean(NatuurTools.PAR_AANMAAK);
    behoud    = paramBundle.getBoolean(NatuurTools.PAR_BEHOUD);
    hernummer = paramBundle.getBoolean(NatuurTools.PAR_HERNUMMER);

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_WIJZIGEN));
    if (behoud) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_SKIPSTRUCTUUR));
    }
    if (aanmaak) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_AANMAKEN));
    }
    if (hernummer) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_HERNUMMER));
    }
    DoosUtils.naarScherm();
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
    var latijnsenaam  = json.get(NatuurTools.KEY_LATIJN).toString();
    var rang          = json.get(NatuurTools.KEY_RANG).toString();
    var seq           =
        Integer.valueOf(json.get(NatuurTools.KEY_SEQ).toString());

    TaxonDto  taxon = getTaxon(latijnsenaam, parentId, seq, rang);
    controleerHierarchie(taxon, parentId, seq);

    if (null == taxon.getTaxonId()) {
      return;
    }

    if (json.containsKey(NatuurTools.KEY_NAMEN)) {
      controleerTaxonnamen(taxon, (JSONObject) json.get(NatuurTools.KEY_NAMEN));
    }
    if (json.containsKey(NatuurTools.KEY_SUBRANGEN)) {
      for (var subrang : (JSONArray) json.get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(taxon.getTaxonId(), (JSONObject) subrang);
      }
    }
  }
}
