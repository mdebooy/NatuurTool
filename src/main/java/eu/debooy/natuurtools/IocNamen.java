/**
 * Copyright 2020 Marco de Booij
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
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class IocNamen extends Batchjob {
  private static final  JSONParser      parser          = new JSONParser();
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private IocNamen() {}

  public static void execute(String[] args) {
    CsvBestand    csvBestand;
    JSONObject    familie           = new JSONObject();
    JSONArray     families          = new JSONArray();
    JSONObject    namen             = new JSONObject();
    int           nFamilies         = 0;
    int           nOrdes            = 0;
    int           nSoorten          = 0;
    JSONObject    orde              = new JSONObject();
    JSONArray     ordes             = new JSONArray();
    JSONObject    soort             = new JSONObject();
    JSONArray     soorten           = new JSONArray();
    JSONObject    taxa              = new JSONObject();

    Banner.printDoosBanner(resourceBundle.getString("banner.iocnamen"));

    if (!setParameters(args)) {
      return;
    }

    String[]      taal              = parameters.get(NatuurTools.PAR_TALEN)
                                                .split(",");

    try {
      csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(parameters.get(PAR_INVOERDIR) +
                                    parameters.get(NatuurTools.PAR_IOCBESTAND)
                                    + EXT_CSV)
                        .setCharset(parameters.get(PAR_CHARSETIN))
                        .setHeader(false)
                        .build();
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      return;
    }

    int lijnen  = skipHeader(csvBestand);

    while (csvBestand.hasNext()) {
      lijnen++;
      try {
        String[]  veld = csvBestand.next();
        // Nieuwe Orde
        if (DoosUtils.isNotBlankOrNull(veld[1])) {
          nieuweOrde(soort, namen, soorten, familie, families, orde, ordes);
          nOrdes++;
          orde.put(NatuurTools.KEY_ID, nOrdes);
          orde.put(NatuurTools.KEY_ORDE, veld[1]);
        }
        // Nieuwe familie
        if (DoosUtils.isNotBlankOrNull(veld[2])) {
          nieuweFamilie(soort, namen, soorten, familie, families);
          nFamilies++;
          familie.put(NatuurTools.KEY_ID, nFamilies);
          familie.put(NatuurTools.KEY_FAMILIE, veld[2]);
        }
        // Nieuw soort
        if (DoosUtils.isNotBlankOrNull(veld[3])) {
          nieuweSoort(soort, namen, soorten);
          nSoorten++;
          soort.put(NatuurTools.KEY_ID, nSoorten);
          soort.put(NatuurTools.KEY_LATIJN, veld[3]);
        }
        for (int i = 0; i < taal.length; i++) {
          if (DoosUtils.isNotBlankOrNull(veld[i+4])) {
            namen.put(taal[i], veld[i+4]);
          }
        }
      } catch (BestandException | ParseException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      }
    }

    try {
      nieuweOrde(soort, namen, soorten, familie, families, orde, ordes);
      taxa.put("taxa", ordes);
    } catch (ParseException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    try {
      csvBestand.close();
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm("csv: " + e.getLocalizedMessage());
    }

    writeJson(taxa);

    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             lijnen));
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_ORDES),
                             nOrdes));
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_FAMILIES),
                             nFamilies));
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_SOORTEN),
                             nSoorten));
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar IocNamen ["
                          + getMelding(LBL_OPTIE)
                          + "] --iocbestand=<"
                          + resourceBundle.getString("label.iocbestand") + ">"
                          + " --talen=<"
                          + resourceBundle.getString("label.talen") + ">",
                         80);
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
                         resourceBundle.getString("help.iocbestand"), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_JSONBESTAND, 12),
                         resourceBundle.getString("help.uitvoerbestand"), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 12),
                         resourceBundle.getString("help.talen"), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_UITVOERDIR, 12),
                         getMelding(HLP_UITVOERDIR), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             NatuurTools.PAR_IOCBESTAND, NatuurTools.PAR_TALEN),
        80);
    DoosUtils.naarScherm();
  }

  private static void nieuweFamilie(JSONObject soort, JSONObject namen,
                                    JSONArray soorten, JSONObject familie,
                                    JSONArray families) throws ParseException {
    nieuweSoort(soort, namen, soorten);
    if (familie.size() > 0) {
      if (soorten.size() > 0) {
        familie.put(NatuurTools.KEY_SOORTEN, parser.parse(soorten.toString()));
        soorten.clear();
      }
      families.add(parser.parse(familie.toString()));
      familie.clear();
    }
  }

  private static void nieuweOrde(JSONObject soort, JSONObject namen,
                                 JSONArray soorten, JSONObject familie,
                                 JSONArray families, JSONObject orde,
                                 JSONArray ordes) throws ParseException {
    nieuweFamilie(soort, namen, soorten, familie, families);
    if (orde.size() > 0) {
      if (families.size() > 0) {
        orde.put(NatuurTools.KEY_FAMILIES, parser.parse(families.toString()));
        families.clear();
      }
      ordes.add(parser.parse(orde.toString()));
      orde.clear();
    }
  }

  private static void nieuweSoort(JSONObject soort, JSONObject namen,
                                  JSONArray soorten) throws ParseException {
    if (soort.size() > 0) {
      if (namen.size() > 0) {
        soort.put(NatuurTools.KEY_NAMEN, parser.parse(namen.toString()));
        namen.clear();
      }
      soorten.add(parser.parse(soort.toString()));
      soort.clear();
    }
  }

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<String>();
    arguments.setParameters(new String[] {PAR_CHARSETIN,
                                          PAR_CHARSETUIT,
                                          PAR_INVOERDIR,
                                          NatuurTools.PAR_IOCBESTAND,
                                          NatuurTools.PAR_JSONBESTAND,
                                          NatuurTools.PAR_TALEN,
                                          PAR_UITVOERDIR});
    arguments.setVerplicht(new String[] {NatuurTools.PAR_IOCBESTAND,
                                         NatuurTools.PAR_TALEN});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    setParameter(arguments, PAR_CHARSETIN,
                 Charset.defaultCharset().name());
    setParameter(arguments, PAR_CHARSETUIT,
                 Charset.defaultCharset().name());
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, NatuurTools.PAR_IOCBESTAND, EXT_CSV);
    setBestandParameter(arguments, NatuurTools.PAR_JSONBESTAND, EXT_JSON);
    if (!hasParameter(NatuurTools.PAR_JSONBESTAND)) {
      setParameter(NatuurTools.PAR_JSONBESTAND,
                   getParameter(NatuurTools.PAR_IOCBESTAND));
    }
    NatuurTools.setTalenParameter(arguments, parameters);
    setDirParameter(arguments, PAR_UITVOERDIR, getParameter(PAR_INVOERDIR));
    if (DoosUtils.nullToEmpty(parameters.get(NatuurTools.PAR_IOCBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              resourceBundle.getString(ERR_BEVATDIRECTORY),
                                       NatuurTools.PAR_IOCBESTAND));
    }
    if (DoosUtils.nullToEmpty(parameters.get(NatuurTools.PAR_JSONBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              resourceBundle.getString(ERR_BEVATDIRECTORY),
                                       NatuurTools.PAR_JSONBESTAND));
    }

    if (fouten.isEmpty()) {
      return true;
    }

    help();
    printFouten(fouten);

    return false;
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

  private static void writeJson(JSONObject taxa) {
    JsonBestand jsonBestand = null;
    try {
      jsonBestand  =
          new JsonBestand.Builder()
                         .setBestand(parameters.get(PAR_UITVOERDIR) +
                                    parameters.get(NatuurTools.PAR_JSONBESTAND)
                                    + EXT_JSON)
                         .setCharset(parameters.get(PAR_CHARSETUIT))
                         .setLezen(false)
                         .build();
      jsonBestand.write(taxa);
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
  }
}
