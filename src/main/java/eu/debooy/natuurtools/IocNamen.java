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
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.exception.BestandException;
import java.io.File;
import java.nio.charset.Charset;
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
                                        totalen = new HashMap<>();

  private static  String[]  taal;
  private static  String    vorigGeslacht = "";

  private IocNamen() {}

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
  }

  public static void execute(String[] args) {
    CsvBestand    csvBestand        = null;
    int           lijnen            = -1;
    JSONObject    taxa              = new JSONObject();

    Banner.printDoosBanner(resourceBundle.getString("banner.iocnamen"));

    if (!setParameters(args)) {
      return;
    }

    taal  = parameters.get(NatuurTools.PAR_TALEN).split(",");
    setRangen();

    try {
      csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(parameters.get(PAR_INVOERDIR) +
                                    parameters.get(NatuurTools.PAR_IOCBESTAND)
                                    + EXT_CSV)
                        .setCharset(parameters.get(PAR_CHARSETIN))
                        .setHeader(false)
                        .build();

      lijnen  = skipHeader(csvBestand);

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
    } finally {
      if (null != csvBestand) {
        try {
          csvBestand.close();
        } catch (BestandException e) {
          DoosUtils.foutNaarScherm("csv close: " + e.getLocalizedMessage());
        }
      }
    }

    NatuurTools.writeJson(parameters.get(PAR_UITVOERDIR)
                           + parameters.get(PAR_JSONBESTAND) + EXT_JSON, taxa,
                          parameters.get(PAR_CHARSETUIT));

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

  private static Integer getVolgnummer(String rang) {
    return totalen.get(rang);
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar IocNamen "
        + getMelding(LBL_OPTIE) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_IOCBESTAND,
              resourceBundle.getString(NatuurTools.LBL_IOCBESTAND)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_TALEN,
              resourceBundle.getString(NatuurTools.LBL_TALEN)), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETIN, 12),
        MessageFormat.format(getMelding(HLP_CHARSETIN),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETUIT, 12),
        MessageFormat.format(getMelding(HLP_CHARSETUIT),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_INVOERDIR, 12),
                         getMelding(HLP_INVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_IOCBESTAND, 12),
                         resourceBundle.getString(NatuurTools.HLP_IOCBESTAND),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_JSONBESTAND, 12),
                         resourceBundle.getString(NatuurTools.HLP_IOCJSON),
                         80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 12),
                         resourceBundle.getString(NatuurTools.HLP_TALEN), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_UITVOERDIR, 12),
                         getMelding(HLP_UITVOERDIR), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             NatuurTools.PAR_IOCBESTAND, NatuurTools.PAR_TALEN),
        80);
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

  private static void nieuwGeslacht()
      throws ParseException {
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

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {PAR_CHARSETIN,
                                          PAR_CHARSETUIT,
                                          PAR_INVOERDIR,
                                          NatuurTools.PAR_IOCBESTAND,
                                          PAR_JSONBESTAND,
                                          NatuurTools.PAR_TALEN,
                                          PAR_UITVOERDIR});
    arguments.setVerplicht(new String[] {NatuurTools.PAR_IOCBESTAND,
                                         NatuurTools.PAR_TALEN});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    parameters  = new HashMap<>();

    setParameter(arguments, PAR_CHARSETIN, Charset.defaultCharset().name());
    setParameter(arguments, PAR_CHARSETUIT, Charset.defaultCharset().name());
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, NatuurTools.PAR_IOCBESTAND, EXT_CSV);
    setBestandParameter(arguments, PAR_JSONBESTAND, EXT_JSON);
    if (!hasParameter(PAR_JSONBESTAND)) {
      setParameter(PAR_JSONBESTAND, getParameter(NatuurTools.PAR_IOCBESTAND));
    }
    NatuurTools.setTalenParameter(arguments, parameters);
    setDirParameter(arguments, PAR_UITVOERDIR, getParameter(PAR_INVOERDIR));

    if (DoosUtils.nullToEmpty(parameters.get(NatuurTools.PAR_IOCBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              getMelding(ERR_BEVATDIRECTORY), NatuurTools.PAR_IOCBESTAND));
    }
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

  private static void setRangen() {
    for (String rang : new String[] {NatuurTools.RANG_ORDE,
                                     NatuurTools.RANG_FAMILIE,
                                     NatuurTools.RANG_GESLACHT,
                                     NatuurTools.RANG_SOORT}) {
      rangen.add(rang);
      totalen.put(rang, 0);
    }
  }

  private static int skipHeader(CsvBestand csvBestand) {
    boolean   einde   = false;
    int       lijnen  = 0;
    String[]  veld;
    while (csvBestand.hasNext() && !einde) {
      try {
        veld  = csvBestand.next();
        lijnen++;
        if (DoosUtils.isNotBlankOrNull(veld[veld.length-1])) {
          einde = true;
        }
      } catch (BestandException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
        return lijnen;
      }
    }

    return lijnen;
  }

  private static void verwerkLijn(String[] veld)
      throws ParseException {
    // Nieuwe Orde
    if (DoosUtils.isNotBlankOrNull(veld[1])) {
      nieuweOrde();
      addRang(NatuurTools.RANG_ORDE);
      orde.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurTools.RANG_ORDE));
      orde.put(NatuurTools.KEY_RANG, NatuurTools.RANG_ORDE);
      orde.put(NatuurTools.KEY_LATIJN,
               veld[1].substring(0, 1).toUpperCase()
               + veld[1].substring(1).toLowerCase());
    }
    // Nieuwe familie
    if (DoosUtils.isNotBlankOrNull(veld[2])) {
      nieuweFamilie();
      addRang(NatuurTools.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurTools.RANG_FAMILIE));
      familie.put(NatuurTools.KEY_RANG, NatuurTools.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_LATIJN,
               veld[2].substring(0, 1).toUpperCase()
               + veld[2].substring(1).toLowerCase());
    }

    // Nieuw soort
    if (DoosUtils.isNotBlankOrNull(veld[3])) {
      String  naam  = veld[3].split(" ")[0];
      // Nieuw geslacht?
      if (!naam.equals(vorigGeslacht)) {
        nieuwGeslacht();
        addRang(NatuurTools.RANG_GESLACHT);
        geslacht.put(NatuurTools.KEY_SEQ,
                     getVolgnummer(NatuurTools.RANG_GESLACHT));
        geslacht.put(NatuurTools.KEY_RANG, NatuurTools.RANG_GESLACHT);
        geslacht.put(NatuurTools.KEY_LATIJN,
                 naam.substring(0, 1).toUpperCase()
                 + naam.substring(1).toLowerCase());
        vorigGeslacht = naam;
      }
      nieuweSoort();
      addRang(NatuurTools.RANG_SOORT);
      soort.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurTools.RANG_SOORT));
      soort.put(NatuurTools.KEY_RANG, NatuurTools.RANG_SOORT);
      soort.put(NatuurTools.KEY_LATIJN,
               veld[3].substring(0, 1).toUpperCase()
               + veld[3].substring(1).toLowerCase());
    }
    for (int i = 0; i < taal.length; i++) {
      if (DoosUtils.isNotBlankOrNull(veld[i+4])) {
        namen.put(taal[i], veld[i+4]);
      }
    }
  }
}
