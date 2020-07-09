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
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.access.JsonBestand;
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
public class IocNamen {
  private static final  JSONParser      parser          = new JSONParser();
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  @SuppressWarnings("unchecked")
  public static void execute(String[] args) {
    CsvBestand    csvBestand;
    JSONObject    familie           = new JSONObject();
    JSONArray     families          = new JSONArray();
    List<String>  fouten            = new ArrayList<String>();
    JsonBestand   jsonBestand       = null;
    int           lijnen            = 3;
    JSONObject    namen             = new JSONObject();
    int           nFamilies         = 0;
    int           nOrdes            = 0;
    int           nSoorten          = 0;
    JSONObject    orde              = new JSONObject();
    JSONArray     ordes             = new JSONArray();
    Map<String, String>
                  parameters        = new HashMap<String, String>();
    JSONObject    soort             = new JSONObject();
    JSONArray     soorten           = new JSONArray();
    JSONObject    taxa              = new JSONObject();

    Banner.printDoosBanner(resourceBundle.getString("banner.iocnamen"));

    verwerkParameters(args, parameters, fouten);

    if (!fouten.isEmpty() ) {
      help();
      fouten.forEach(fout -> {
        DoosUtils.foutNaarScherm(fout);
      });
      return;
    }

    String[]      taal              = parameters.get(NatuurTools.PAR_TALEN)
                                                .split(",");

    try {
      csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(parameters.get(NatuurTools.PAR_INVOERDIR)
                                    + File.separator +
                                    parameters.get(NatuurTools.PAR_IOCBESTAND)
                                    + NatuurTools.EXT_CSV)
                        .setCharset(parameters.get(NatuurTools.PAR_CHARSETIN))
                        .setHeader(false)
                        .build();
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      return;
    }

    // Skip de 3 header regels.
    try {
      csvBestand.next();
      csvBestand.next();
      csvBestand.next();
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      return;
    }
    while (csvBestand.hasNext()) {
      lijnen++;
      try {
        String[] veld = csvBestand.next();
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

    try {
      jsonBestand  =
          new JsonBestand.Builder()
                         .setBestand(parameters.get(NatuurTools.PAR_UITVOERDIR)
                                    + File.separator +
                                    parameters.get(NatuurTools.PAR_JSONBESTAND)
                                    + NatuurTools.EXT_JSON)
                         .setCharset(parameters.get(NatuurTools.PAR_CHARSETUIT))
                         .setLezen(false)
                         .build();
      jsonBestand.write(taxa);
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
    if (null != jsonBestand) {
      try {
        jsonBestand.close();
      } catch (BestandException e) {
        DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
      }
    }

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
    DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_KLAAR));
  }

  protected static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar IocNamen ["
                          + resourceBundle.getString("label.optie")
                          + "] --iocbestand=<"
                          + resourceBundle.getString("label.iocbestand") + ">"
                          + " --talen=<"
                          + resourceBundle.getString("label.talen") + ">",
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm("  --charsetin   ",
        MessageFormat.format(resourceBundle.getString("help.charsetin"),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm("  --charsetuit  ",
        MessageFormat.format(resourceBundle.getString("help.charsetuit"),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm("  --invoerdir   ",
                         resourceBundle.getString("help.invoerdir"), 80);
    DoosUtils.naarScherm("  --iocbestand  ",
                         resourceBundle.getString("help.iocbestand"), 80);
    DoosUtils.naarScherm("  --jsonbestand ",
                         resourceBundle.getString("help.uitvoerbestand"), 80);
    DoosUtils.naarScherm("  --talen       ",
                         resourceBundle.getString("help.talen"), 80);
    DoosUtils.naarScherm("  --uitvoerdir  ",
                         resourceBundle.getString("help.uitvoerdir"), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString("help.paramsverplicht"),
                             NatuurTools.PAR_IOCBESTAND, NatuurTools.PAR_TALEN),
        80);
    DoosUtils.naarScherm();
  }

  @SuppressWarnings("unchecked")
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

  @SuppressWarnings("unchecked")
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

  @SuppressWarnings("unchecked")
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

  private static void verwerkParameterCharsetin(Arguments arguments,
                                                Map<String, String> parameters)
  {
    if (arguments.hasArgument(NatuurTools.PAR_CHARSETIN)) {
      parameters.put(NatuurTools.PAR_CHARSETIN,
                     arguments.getArgument(NatuurTools.PAR_CHARSETIN));
    } else {
      parameters.put(NatuurTools.PAR_CHARSETIN,
                     Charset.defaultCharset().name());
    }
  }

  private static void verwerkParameterCharsetuit(Arguments arguments,
                                                 Map<String, String> parameters)
  {
    if (arguments.hasArgument(NatuurTools.PAR_CHARSETUIT)) {
      parameters.put(NatuurTools.PAR_CHARSETUIT,
                     arguments.getArgument(NatuurTools.PAR_CHARSETUIT));
    } else {
      parameters.put(NatuurTools.PAR_CHARSETUIT,
                     Charset.defaultCharset().name());
    }
  }

  private static void verwerkParameterInvoerdir(Arguments arguments,
                                                Map<String, String> parameters)
  {
    String  parameter;
    if (arguments.hasArgument(NatuurTools.PAR_INVOERDIR)) {
      parameter = arguments.getArgument(NatuurTools.PAR_INVOERDIR);
      if (parameter.endsWith(File.separator)) {
        parameter   = parameter.substring(0, parameter.length()
                                             - File.separator.length());
      }
      parameters.put(NatuurTools.PAR_INVOERDIR, parameter);
    } else {
      parameters.put(NatuurTools.PAR_INVOERDIR, ".");
    }
  }

  private static void verwerkParameterIocbestand(Arguments arguments,
                                                 Map<String, String> parameters,
                                                 List<String> fouten) {
    String  parameter;
    if (arguments.hasArgument(NatuurTools.PAR_IOCBESTAND)) {
      parameter = arguments.getArgument(NatuurTools.PAR_IOCBESTAND);
      if (parameter.endsWith(NatuurTools.EXT_CSV)) {
        parameter  =
            parameter.substring(0, parameter.length()
                                     - NatuurTools.EXT_CSV.length());
      }
      if (parameter.contains(File.separator)) {
        fouten.add(
          MessageFormat.format(
              resourceBundle.getString(NatuurTools.ERR_BEVATDIRECTORY),
                                       NatuurTools.PAR_IOCBESTAND));
      }
      parameters.put(NatuurTools.PAR_IOCBESTAND, parameter);
    }
  }

  private static void verwerkParameterJsonbestand(Arguments arguments,
                                                  Map<String,
                                                      String> parameters,
                                                  List<String> fouten) {
    String  parameter;
    if (arguments.hasArgument(NatuurTools.PAR_JSONBESTAND)) {
      parameter = arguments.getArgument(NatuurTools.PAR_JSONBESTAND);
      if (parameter.endsWith(NatuurTools.EXT_JSON)) {
        parameter  =
            parameter.substring(0, parameter.length()
                                     - NatuurTools.EXT_JSON.length());
      }
      parameters.put(NatuurTools.PAR_JSONBESTAND, parameter);
      if (parameter.contains(File.separator)) {
        fouten.add(
          MessageFormat.format(
              resourceBundle.getString(NatuurTools.ERR_BEVATDIRECTORY),
                                       NatuurTools.PAR_JSONBESTAND));
      }
    } else {
      parameters.put(NatuurTools.PAR_JSONBESTAND,
                     parameters.get(NatuurTools.PAR_IOCBESTAND));
    }
  }

  private static void verwerkParameterTalen(Arguments arguments,
                                            Map<String, String> parameters) {
    if (arguments.hasArgument(NatuurTools.PAR_TALEN)) {
      parameters.put(NatuurTools.PAR_TALEN,
                     arguments.getArgument(NatuurTools.PAR_TALEN)
                              .toLowerCase()
                              .replaceAll("[^a-z,]", ""));
    }
  }

  private static void verwerkParameterUitvoerdir(Arguments arguments,
                                                 Map<String, String> parameters)
  {
    String  parameter;
    if (arguments.hasArgument(NatuurTools.PAR_UITVOERDIR)) {
      parameter = arguments.getArgument(NatuurTools.PAR_UITVOERDIR);
      if (parameter.endsWith(File.separator)) {
        parameter   = parameter.substring(0, parameter.length()
                                             - File.separator.length());
      }
      parameters.put(NatuurTools.PAR_UITVOERDIR, parameter);
    } else {
      parameters.put(NatuurTools.PAR_UITVOERDIR, ".");
    }
  }

  private static void verwerkParameters(String[] args,
                                        Map<String, String> parameters,
                                        List<String> fouten) {
    Arguments     arguments = new Arguments(args);

    arguments.setParameters(new String[] {NatuurTools.PAR_CHARSETIN,
                                          NatuurTools.PAR_CHARSETUIT,
                                          NatuurTools.PAR_INVOERDIR,
                                          NatuurTools.PAR_IOCBESTAND,
                                          NatuurTools.PAR_JSONBESTAND,
                                          NatuurTools.PAR_TALEN,
                                          NatuurTools.PAR_UITVOERDIR});
    arguments.setVerplicht(new String[] {NatuurTools.PAR_IOCBESTAND,
                                         NatuurTools.PAR_TALEN});
    if (!arguments.isValid()) {
      fouten.add(resourceBundle.getString(NatuurTools.ERR_INVALIDPARAMS));
    }

    verwerkParameterCharsetin(arguments, parameters);
    verwerkParameterCharsetuit(arguments, parameters);
    verwerkParameterInvoerdir(arguments, parameters);
    verwerkParameterIocbestand(arguments, parameters, fouten);
    verwerkParameterJsonbestand(arguments, parameters, fouten);
    verwerkParameterTalen(arguments, parameters);
    verwerkParameterUitvoerdir(arguments, parameters);
 }
}
