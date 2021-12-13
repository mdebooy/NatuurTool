/*
 * Copyright (c) 2021 Marco de Booij
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
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.components.Message;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class CsvNaarJson extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  Map<String, JSONObject> jsonRang    = new HashMap<>();
  private static final  Map<String, JSONArray>  jsonRangen  = new HashMap<>();
  private static final  JSONParser              parser      = new JSONParser();
  private static final  List<String>            rangen      = new ArrayList<>();
  private static final  Map<String, Integer>    totalen     = new HashMap<>();

  private static  EntityManager em;
  private static  Integer       lijnen      = 0;
  private static  String        taal        = "";
  private static  String        vorigeRang  = "";

  protected CsvNaarJson() {}

  private static void controleerHierarchie(String rang, String latijnsenaam)
      throws ParseException {
    // Genereer het geslacht als deze niet in het bestand staat.
    if (rang.equals(NatuurTools.RANG_SOORT)) {
      genereerRang(NatuurTools.RANG_GESLACHT, latijnsenaam.split(" ")[0]);
    }

    // Genereer het soort als deze niet in het bestand staat.
    if (rang.equals(NatuurTools.RANG_ONDERSOORT)) {
      String[]  woorden = latijnsenaam.split(" ");
      genereerRang(NatuurTools.RANG_SOORT, woorden[0] + " " + woorden[1]);
    }

    if (!rang.equals(vorigeRang)) {
      if (rangen.indexOf(vorigeRang) > rangen.indexOf(rang)) {
        samenvoegen(rang);
      }
      vorigeRang  = rang;
    }
  }

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_CSVNAARJSON)
                           .setClassloader(IocCheck.class.getClassLoader())
                           .setValidator(new ParameterAfwezig())
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    em    = NatuurTools.getEntityManager(
                paramBundle.getString(NatuurTools.PAR_DBUSER),
                paramBundle.getString(NatuurTools.PAR_DBURL),
                paramBundle.getString(NatuurTools.PAR_WACHTWOORD));

    taal  = paramBundle.getString(PAR_TAAL);

    var taxoninfo = paramBundle.getString(NatuurTools.PAR_TAXAROOT).split(",");
    getRangen();

    var         namen       = new JSONObject();
    TaxonDto    parent      = NatuurTools.getTaxon(taxoninfo[1], em);
    String      root        = parent.getRang();
    vorigeRang  = root;

    jsonRang.get(root).put(NatuurTools.KEY_LATIJN, parent.getLatijnsenaam());
    jsonRang.get(root).put(NatuurTools.KEY_RANG, root);
    jsonRang.get(root).put(NatuurTools.KEY_SEQ, parent.getVolgnummer());
    parent.getTaxonnamen().forEach(naam -> namen.put(naam.getTaal(),
                                                     naam.getNaam()));
    jsonRang.get(root).put(NatuurTools.KEY_NAMEN, namen);

    try (var csvBestand =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(PAR_CSVBESTAND,
                                                   BestandConstants.EXT_CSV))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(false)
                        .build()) {
      verwerkCsv(csvBestand);
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    try {
      samenvoegen(root);

      NatuurTools.writeJson(paramBundle.getBestand(PAR_JSONBESTAND,
                                                   BestandConstants.EXT_JSON),
                            jsonRang.get(root),
                            paramBundle.getString(PAR_CHARSETUIT));
    } catch (ParseException e) {
      DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
    }

    em.close();

    DoosUtils.naarScherm();
    rangen.forEach(rang -> {
      Integer volgnummer  = totalen.get(rang);
      if (volgnummer > 0) {
        DoosUtils.naarScherm(String.format("%6s: %,6d",
                rang, totalen.get(rang)));
      }
    });
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             String.format("%,6d", lijnen)));
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
    DoosUtils.naarScherm();
  }

  private static void genereerRang(String rang, String latijnsenaam)
      throws ParseException {
    if (!jsonRang.get(rang).isEmpty()) {
      if (!jsonRang.get(rang).get(NatuurTools.KEY_LATIJN)
                  .equals(latijnsenaam)) {
        verwerkTaxon(rang, latijnsenaam, "");
      }
    } else {
      verwerkTaxon(rang, latijnsenaam, "");
    }
  }

  private static void getRangen() {
    List<RangDto> ranglijst = em.createQuery(NatuurTools.QRY_RANG)
                                .getResultList();

    ranglijst.forEach(rang -> {
      jsonRang.put((rang.getRang()), new JSONObject());
      jsonRangen.put((rang.getRang()), new JSONArray());
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
  }

  private static Integer getVolgnummer(String rang) {
    Integer volgnummer  = totalen.get(rang) + 1;

    totalen.put(rang, volgnummer);

    return volgnummer;
  }

  protected static String naarRang(String rang, String laatste)
      throws ParseException {
    if (!jsonRangen.get(laatste).isEmpty()) {
      jsonRang.get(rang).put(NatuurTools.KEY_SUBRANGEN,
                             parser.parse(jsonRangen.get(laatste)
                                                    .toString()));
      jsonRangen.get(laatste).clear();
    }
    jsonRangen.get(rang).add(parser.parse(jsonRang.get(rang)
                                                  .toString()));
    jsonRang.get(rang).clear();
    return rang;
  }

  protected static String naarRangen(String rang, String laatste)
      throws ParseException {
    for (Object subrang : (JSONArray) jsonRangen.get(laatste)) {
      jsonRangen.get(rang).add(parser.parse(subrang.toString()));
    }
    jsonRangen.get(laatste).clear();

    return rang;
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.toString())));
  }

  private static void samenvoegen(String root) throws ParseException {
    Integer iRoot   = rangen.indexOf(root);
    String  laatste = rangen.get(rangen.size()-1);

    // Laagste rang kan geen subrangen hebben.
    if (!jsonRang.get(laatste).isEmpty()) {
      jsonRangen.get(laatste).add(parser.parse(jsonRang.get(laatste)
                                                       .toString()));
      jsonRang.get(laatste).clear();
    }

    for (int i = rangen.size()-2; i > iRoot; i--) {
      String  rang    = rangen.get(i);
      if (!jsonRang.get(rang).isEmpty()) {
        laatste = naarRang(rang, laatste);
      } else {
        if (!jsonRangen.get(rang).isEmpty()
            && !jsonRangen.get(laatste).isEmpty()) {
          laatste = naarRangen(rang, laatste);
        }
      }
    }

    if (!jsonRang.get(root).isEmpty()) {
      jsonRang.get(root).put(NatuurTools.KEY_SUBRANGEN,
                             parser.parse(jsonRangen.get(laatste).toString()));
    } else {
      for (Object subrang : (JSONArray) jsonRangen.get(laatste)) {
        jsonRangen.get(root).add(parser.parse(subrang.toString()));
      }
    }
    jsonRangen.get(laatste).clear();
  }

  private static void verwerkCsv(CsvBestand csvBestand)
      throws BestandException, ParseException {
    while (csvBestand.hasNext()) {
      lijnen++;
      var veld          = csvBestand.next();
      var rang          = veld[0];
      var latijnsenaam  = veld[1];
      var naam          = veld[2];

      verwerkTaxon(rang, latijnsenaam, naam);
    }
  }

  private static void verwerkTaxon(String rang, String latijnsenaam,
                                   String naam)
      throws ParseException {
    var     namen       = new JSONObject();
    Integer volgnummer  = getVolgnummer(rang);

    controleerHierarchie(rang, latijnsenaam);

    if (!jsonRang.get(rang).isEmpty()) {
      jsonRangen.get(rang).add(parser.parse(jsonRang.get(rang).toString()));
      jsonRang.get(rang).clear();
    }

    if (DoosUtils.isNotBlankOrNull(naam)) {
      namen.put(taal, naam);
    }
    jsonRang.get(rang).put(NatuurTools.KEY_LATIJN, latijnsenaam);
    if (!namen.isEmpty()) {
      jsonRang.get(rang).put(NatuurTools.KEY_NAMEN,
                             parser.parse(namen.toString()));
    }
    jsonRang.get(rang).put(NatuurTools.KEY_RANG, rang);
    jsonRang.get(rang).put(NatuurTools.KEY_SEQ, volgnummer);
  }
}