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
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.exception.BestandException;
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
public class IocNamen extends Batchjob {
  private static final  JSONObject      familie         = new JSONObject();
  private static final  JSONArray       families        = new JSONArray();
  private static final  JSONObject      geslacht        = new JSONObject();
  private static final  JSONArray       geslachten      = new JSONArray();
  private static final  JSONObject      namen           = new JSONObject();
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

  private static  Integer   sequence      = 0;
  private static  String[]  taal;
  private static  String    vorigeFamilie = "";
  private static  String    vorigeOrde    = "";
  private static  String    vorigGeslacht = "";

  protected IocNamen() {}

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
    sequence++;
  }

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_IOCNAMEN)
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    taal  = paramBundle.getString(NatuurTools.PAR_TALEN).split(",");
    setRangen();

    var taxa    = new JSONObject();
    var lijnen  = verwerkBestand(taxa);

    NatuurTools.writeJson(paramBundle.getBestand(NatuurTools.PAR_JSON),
                          taxa, paramBundle.getString(PAR_CHARSETUIT));

    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             String.format("%,6d", lijnen)));
    rangen.forEach(rang -> {
      if (totalen.get(rang) > 0) {
        DoosUtils.naarScherm(String.format("%6s: %,6d",
                                           rang, totalen.get(rang)));
      }
    });
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
    DoosUtils.naarScherm();
  }

  private static void nieuweFamilie() throws ParseException {
    nieuwGeslacht();
    if (!familie.isEmpty()) {
      if (!geslachten.isEmpty()) {
        familie.put(NatuurTools.KEY_SUBRANGEN,
                    parser.parse(geslachten.toString()));
        geslachten.clear();
      }
      families.add(parser.parse(familie.toString()));
      familie.clear();
    }
  }

  private static void nieuweOrde() throws ParseException {
    nieuweFamilie();
    if (!orde.isEmpty()) {
      if (!families.isEmpty()) {
        orde.put(NatuurTools.KEY_SUBRANGEN, parser.parse(families.toString()));
        families.clear();
      }
      ordes.add(parser.parse(orde.toString()));
      orde.clear();
    }
  }

  private static void nieuweSoort() throws ParseException {
    if (!soort.isEmpty()) {
      if (!namen.isEmpty()) {
        soort.put(NatuurTools.KEY_NAMEN, parser.parse(namen.toString()));
        namen.clear();
      }
      soorten.add(parser.parse(soort.toString()));
      soort.clear();
    }
  }

  private static void nieuwGeslacht() throws ParseException {
    nieuweSoort();
    if (!geslacht.isEmpty()) {
      if (!soorten.isEmpty()) {
        geslacht.put(NatuurTools.KEY_SUBRANGEN,
                     parser.parse(soorten.toString()));
        soorten.clear();
      }
      geslachten.add(parser.parse(geslacht.toString()));
      geslacht.clear();
    }

  }

  private static void setRangen() {
    for (String rang : new String[] {NatuurTools.RANG_ORDE,
                                     NatuurTools.RANG_FAMILIE,
                                     NatuurTools.RANG_GESLACHT,
                                     NatuurTools.RANG_SOORT}) {
      rangen.add(rang);
      totalen.put(rang, 0);
    }
  }

  private static int verwerkBestand(JSONObject taxa) {
    var lijnen  = 0;
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_IOCBESTAND))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(true)
                        .build()) {

      while (csvBestand.hasNext()) {
        lijnen++;
        verwerkLijn(csvBestand.next());
      }

      nieuweOrde();
      taxa.put(NatuurTools.KEY_RANG, NatuurTools.RANG_KLASSE);
      taxa.put(NatuurTools.KEY_LATIJN, "Aves");
      taxa.put(NatuurTools.KEY_SUBRANGEN, ordes);
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm("csv: " + e.getLocalizedMessage());
    }

    return lijnen;
  }

  private static void verwerkLijn(String[] veld) throws ParseException {
    // Nieuwe Orde
    if (!veld[1].equals(vorigeOrde)) {
      nieuweOrde();
      addRang(NatuurTools.RANG_ORDE);
      orde.put(NatuurTools.KEY_SEQ, sequence);
      orde.put(NatuurTools.KEY_RANG, NatuurTools.RANG_ORDE);
      orde.put(NatuurTools.KEY_LATIJN,
               veld[1].substring(0, 1).toUpperCase()
               + veld[1].substring(1).toLowerCase());
      vorigeOrde  = veld[1];
    }
    // Nieuwe familie
    if (!veld[2].equals(vorigeFamilie)) {
      nieuweFamilie();
      addRang(NatuurTools.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_SEQ, sequence);
      familie.put(NatuurTools.KEY_RANG, NatuurTools.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_LATIJN,
               veld[2].substring(0, 1).toUpperCase()
               + veld[2].substring(1).toLowerCase());
      vorigeFamilie = veld[2];
    }

    // Nieuw soort
    var naam  = veld[3].split(" ")[0];
    // Nieuw geslacht?
    if (!naam.equals(vorigGeslacht)) {
      nieuwGeslacht();
      addRang(NatuurTools.RANG_GESLACHT);
      geslacht.put(NatuurTools.KEY_SEQ, sequence);
      geslacht.put(NatuurTools.KEY_RANG, NatuurTools.RANG_GESLACHT);
      geslacht.put(NatuurTools.KEY_LATIJN,
               naam.substring(0, 1).toUpperCase()
               + naam.substring(1).toLowerCase());
      vorigGeslacht = naam;
    }
    nieuweSoort();
    addRang(NatuurTools.RANG_SOORT);
    soort.put(NatuurTools.KEY_SEQ, sequence);
    soort.put(NatuurTools.KEY_RANG, NatuurTools.RANG_SOORT);
    soort.put(NatuurTools.KEY_LATIJN,
             veld[3].substring(0, 1).toUpperCase()
             + veld[3].substring(1).toLowerCase());

    for (var i = 0; i < taal.length; i++) {
      if (DoosUtils.isNotBlankOrNull(veld[i+4])) {
        namen.put(taal[i], veld[i+4]);
      }
    }
  }
}
