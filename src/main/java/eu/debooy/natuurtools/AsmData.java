/*
 * Copyright (c) 2024 Marco de Booij
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

import eu.debooy.doosutils.Batchjob;
import static eu.debooy.doosutils.Batchjob.PAR_CHARSETIN;
import static eu.debooy.doosutils.Batchjob.PAR_CHARSETUIT;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.NatuurUtils;
import eu.debooy.natuur.domain.TaxonDto;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class AsmData extends Batchjob {
  private static final  JSONObject      familie         = new JSONObject();
  private static final  JSONArray       families        = new JSONArray();
  private static final  JSONObject      geslacht        = new JSONObject();
  private static final  JSONArray       geslachten      = new JSONArray();
  private static final  JSONObject      orde            = new JSONObject();
  private static final  JSONArray       ordes           = new JSONArray();
  private static final  JSONParser      parser          = new JSONParser();
  private static final  List<String>    rangen          = new ArrayList<>();
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());
  private static final  JSONObject      soort           = new JSONObject();
  private static final  JSONArray       soorten         = new JSONArray();
  private static final  Map<String, Integer>
                                        totalen         = new HashMap<>();

  private static  Integer   factor        = NatuurConstants.VOLGNUMMERFACTOR;
  private static  Integer   lijnen        = 0;
  private static  boolean   perRang       = false;
  private static  Integer   sequence      = 0;
  private static  String    asmtaal       = "";
  private static  String    vorigeFamilie = "";
  private static  String    vorigeOrde    = "";
  private static  String    vorigGeslacht = "";

  protected AsmData() {}

  static class AsmTaxon {
    private String  familie;
    private String  geslacht;
    private String  naam;
    private String  orde;
    private String  soort;
    private boolean uitgestorven;

    public String getFamilie() {
      return DoosUtils.nullToEmpty(familie);
    }

    public String getGeslacht() {
      return DoosUtils.nullToEmpty(geslacht);
    }

    public String getNaam() {
      return naam;
    }

    public String getOrde() {
      return DoosUtils.nullToEmpty(orde);
    }

    public String getSoort() {
      return DoosUtils.nullToEmpty(soort);
    }

    public boolean isUitgestorven() {
      return uitgestorven;
    }

    public void setFamilie(String familie) {
      this.familie      = NatuurUtils.formatLatijnsenaam(familie);
    }

    public void setGeslacht(String geslacht) {
      this.geslacht     = NatuurUtils.formatLatijnsenaam(geslacht);
    }

    public void setNaam(String naam) {
      this.naam         = DoosUtils.nullToEmpty(naam).trim();
    }

    public void setOrde(String orde) {
      this.orde         = NatuurUtils.formatLatijnsenaam(orde);
    }

    public void setSoort(String soort) {
      this.soort        = soort.toLowerCase();
    }

    public void setUitgestorven(boolean uitgestorven) {
      this.uitgestorven = uitgestorven;
    }
  }

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
    sequence++;
  }

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_ASMDATA)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    asmtaal     = paramBundle.getString(NatuurTools.PAR_TAAL);
    perRang     = paramBundle.getBoolean(NatuurTools.PAR_PERRANG);

    setRangen();

    if (paramBundle.containsArgument(NatuurTools.PAR_FACTOR)) {
      factor  = paramBundle.getInteger(NatuurTools.PAR_FACTOR);
    }

    init();
    var taxa    = new JSONObject();
    verwerkAsmbestand(taxa);

    NatuurTools.writeJson(paramBundle.getBestand(NatuurTools.PAR_JSON),
                          taxa, paramBundle.getString(PAR_CHARSETUIT));

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             String.format("%,9d", lijnen)));
    rangen.forEach(rang -> {
      if (totalen.get(rang) > 0) {
        DoosUtils.naarScherm(String.format("%6s : %,9d",
                                           rang, totalen.get(rang)));
      }
    });

    klaar();
  }

  private static int getVolgnummer(String rang) {
    if (!perRang) {
      return sequence;
    }

    return totalen.get(rang);
  }

  private static void init() {
    if (!paramBundle.containsArgument(NatuurTools.PAR_DBURL)) {
      sequence  = factor
                    * paramBundle.getInteger(NatuurTools.PAR_KLASSEVOLGNUMMER);

      return;
    }

    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var em              = dbConn.getEntityManager();

      var query           = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
      query.setParameter(TaxonDto.PAR_LATIJNSENAAM,
                         NatuurConstants.LAT_ZOOGDIEREN);
      var klasse          = (TaxonDto) query.getSingleResult();
      sequence            = factor * klasse.getVolgnummer().intValue();
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }

  private static void nieuwGeslacht(AsmTaxon asmTaxon) throws ParseException {
    if (!geslacht.isEmpty()) {
      if (!soorten.isEmpty()) {
        geslacht.put(NatuurTools.KEY_SUBRANGEN,
                     parser.parse(soorten.toString()));
        soorten.clear();
      }
      geslachten.add(parser.parse(geslacht.toString()));
      geslacht.clear();
    }

    if (!asmTaxon.getFamilie().equals(vorigeFamilie)) {
      nieuweFamilie(asmTaxon);
    }

    if (null == asmTaxon.getNaam()) {
      return;
    }

    addRang(NatuurConstants.RANG_GESLACHT);
    vorigGeslacht = asmTaxon.getGeslacht();
    geslacht.put(NatuurTools.KEY_SEQ,
                 getVolgnummer(NatuurConstants.RANG_GESLACHT));
    geslacht.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_GESLACHT);
    geslacht.put(NatuurTools.KEY_LATIJN, vorigGeslacht);
  }

  private static void nieuweFamilie(AsmTaxon asmTaxon) throws ParseException {
    if (!familie.isEmpty()) {
      if (!geslachten.isEmpty()) {
        familie.put(NatuurTools.KEY_SUBRANGEN,
                    parser.parse(geslachten.toString()));
        geslachten.clear();
      }
      families.add(parser.parse(familie.toString()));
      familie.clear();
    }

    if (!asmTaxon.getOrde().equals(vorigeOrde)) {
      nieuweOrde(asmTaxon);
    }

    if (null == asmTaxon.getNaam()) {
      return;
    }

    addRang(NatuurConstants.RANG_FAMILIE);
    vorigeFamilie = asmTaxon.getFamilie();
    familie.put(NatuurTools.KEY_SEQ,
                getVolgnummer(NatuurConstants.RANG_FAMILIE));
    familie.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_FAMILIE);
    familie.put(NatuurTools.KEY_LATIJN, vorigeFamilie);
  }

  private static void nieuweOrde(AsmTaxon asmTaxon) throws ParseException {
    if (!orde.isEmpty()) {
      if (!families.isEmpty()) {
        orde.put(NatuurTools.KEY_SUBRANGEN, parser.parse(families.toString()));
        families.clear();
      }
      ordes.add(parser.parse(orde.toString()));
      orde.clear();
    }

    if (null == asmTaxon.getNaam()) {
      return;
    }

    addRang(NatuurConstants.RANG_ORDE);
    vorigeOrde    = asmTaxon.getOrde();
    orde.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurConstants.RANG_ORDE));
    orde.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_ORDE);
    orde.put(NatuurTools.KEY_LATIJN, vorigeOrde);
  }

  private static void nieuweSoort(AsmTaxon asmTaxon) throws ParseException {
    if (!soort.isEmpty()) {
      soorten.add(parser.parse(soort.toString()));
      soort.clear();
    }

    if (!asmTaxon.getGeslacht().equals(vorigGeslacht)) {
      nieuwGeslacht(asmTaxon);
    }

    if (null == asmTaxon.getNaam()) {
      return;
    }

    var latijn  = String.format("%s %s", asmTaxon.getGeslacht(),
                                         asmTaxon.getSoort());

    addRang(NatuurConstants.RANG_SOORT);
    soort.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurConstants.RANG_SOORT));
    soort.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_SOORT);
    soort.put(NatuurTools.KEY_LATIJN, latijn);
    soort.put(NatuurTools.KEY_UITGESTORVEN, asmTaxon.isUitgestorven());
    var namen = new JSONObject();
    namen.put(asmtaal, asmTaxon.getNaam());
    soort.put(NatuurTools.KEY_NAMEN, namen);
  }

  private static void setRangen() {
    for (String rang : new String[] {NatuurConstants.RANG_ORDE,
                                     NatuurConstants.RANG_FAMILIE,
                                     NatuurConstants.RANG_GESLACHT,
                                     NatuurConstants.RANG_SOORT}) {
      rangen.add(rang);
      totalen.put(rang, 0);
    }
  }

  private static void verwerkAsmbestand(JSONObject taxa) {
    try (var jsonBestand  =
          new JsonBestand.Builder()
                         .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_ASMBESTAND))
                         .setCharset(paramBundle.getString(PAR_CHARSETIN))
                         .build()) {
      var json  = (JSONArray) jsonBestand.read();

      json.forEach(taxon -> verwerkTaxon((JSONObject) taxon));

      nieuweSoort(new AsmTaxon());
      taxa.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_KLASSE);
      taxa.put(NatuurTools.KEY_LATIJN, NatuurConstants.LAT_ZOOGDIEREN);
      taxa.put(NatuurTools.KEY_SUBRANGEN, ordes);
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_IOCNAMEN),
                                             e.getLocalizedMessage()));
    }
  }

  private static void verwerkTaxon(JSONObject taxon) {
    var asmTaxon  = new AsmTaxon();

    asmTaxon.setFamilie(taxon.get("family").toString());
    asmTaxon.setGeslacht(taxon.get("genus").toString());
    asmTaxon.setOrde(taxon.get("order").toString());
    asmTaxon.setSoort(taxon.get("specificEpithet").toString());
    asmTaxon.setNaam(taxon.get("mainCommonName").toString());
    asmTaxon.setUitgestorven(taxon.get("extinct").equals("1"));

    try {
      nieuweSoort(asmTaxon);
    } catch (ParseException e) {
      DoosUtils.foutNaarScherm(String.format("%s %s  - %s",
                                             asmTaxon.getGeslacht(),
                                             asmTaxon.getSoort(),
                                             e.getLocalizedMessage()));
    }

    lijnen++;
  }
}
