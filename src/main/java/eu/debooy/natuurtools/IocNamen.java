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

import eu.debooy.doos.domain.TaalDto;
import eu.debooy.doos.domain.TaalnaamDto;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
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

  private static  boolean   perRang       = false;
  private static  Integer   sequence      = 0;
  private static  String[]  taalkolom;
  private static  String    vorigeFamilie = "";
  private static  String    vorigeOrde    = "";
  private static  String    vorigGeslacht = "";

  private static final  Set<String> taal      = new TreeSet<>();
  private static final  Set<String> taalnaam  = new TreeSet<>();

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

    perRang = paramBundle.getBoolean(NatuurTools.PAR_PERRANG);
    if (paramBundle.containsArgument(NatuurTools.PAR_TALEN)) {
      taal.addAll(Set.of(paramBundle.getString(NatuurTools.PAR_TALEN)
                                    .split(",")));
    }
    setRangen();

    var taxa    = new JSONObject();
    var lijnen  = verwerkBestand(taxa);

    NatuurTools.writeJson(paramBundle.getBestand(NatuurTools.PAR_JSON),
                          taxa, paramBundle.getString(PAR_CHARSETUIT));

    var melding =
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_TALEN),
                             taalnaam).replace("[, ", "[");
    DoosUtils.naarScherm(melding.indexOf("[") + 1, melding, 80);
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             String.format("%,6d", lijnen)));
    rangen.forEach(rang -> {
      if (totalen.get(rang) > 0) {
        DoosUtils.naarScherm(String.format("%6s: %,6d",
                                           rang, totalen.get(rang)));
      }
    });

    klaar();
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

  private static int getVolgnummer(String rang) {
    if (!perRang) {
      return sequence;
    }

    return totalen.get(rang);
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

      taalkolom = Arrays.copyOfRange(csvBestand.getKolomNamen(), 4,
                                     csvBestand.getKolomNamen().length);
      verwerkHeader();

      if (taalkolom.length > 0) {
        while (csvBestand.hasNext()) {
          lijnen++;
          verwerkLijn(csvBestand.next());
        }

        nieuweOrde();
        taxa.put(NatuurTools.KEY_RANG, NatuurTools.RANG_KLASSE);
        taxa.put(NatuurTools.KEY_LATIJN, "Aves");
        taxa.put(NatuurTools.KEY_SUBRANGEN, ordes);
      }
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm("csv: " + e.getLocalizedMessage());
    }

    return lijnen;
  }

  private static void verwerkHeader() {
    if (!paramBundle.containsArgument(NatuurTools.PAR_DBURL)) {
      taalkolom = paramBundle.getString(NatuurTools.PAR_TALEN).split(",");
      taalnaam.addAll(Set.of(taalkolom));
      return;
    }

    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var csvtaal         = paramBundle.getString(PAR_TAAL);
      var em              = dbConn.getEntityManager();
      var gebruikerstaal  =
          ((TaalDto)em.createNamedQuery(TaalDto.QRY_TAAL_ISO6391)
                      .setParameter(TaalDto.PAR_ISO6391,
                                    Locale.getDefault().getLanguage())
                      .getSingleResult()).getIso6392t();
      var naamquery       = em.createNamedQuery(TaalnaamDto.QRY_METTAAL);

      for (var i =0; i < taalkolom.length; i++) {
        naamquery.setParameter(TaalnaamDto.PAR_TAAL, csvtaal);
        naamquery.setParameter(TaalnaamDto.PAR_NAAM, taalkolom[i]);
        var taalnaamDto = naamquery.getResultList();

        if (taalnaamDto.isEmpty()) {
          taalkolom[i]  = "";
          continue;
        }

        var taalDto = em.find(TaalDto.class,
                              ((TaalnaamDto)taalnaamDto.get(0)).getTaalId());
        if (taal.isEmpty()
            || taal.contains(taalDto.getIso6391())) {
          taalkolom[i]  = taalDto.getIso6391();
          if (taalDto.hasTaalnaam(gebruikerstaal)) {
            taalnaam.add(String.format("%s (%s)",
                                       taalDto.getNaam(gebruikerstaal),
                                       taalDto.getIso6391()));
          } else {
            taalnaam.add(taalkolom[i]);
          }
        } else {
          taalkolom[i]  = "";
        }
      }
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      taalkolom = new String[0];
    }
  }

  private static void verwerkLijn(String[] veld) throws ParseException {
    // Nieuwe Orde
    if (!veld[1].equals(vorigeOrde)) {
      nieuweOrde();
      addRang(NatuurTools.RANG_ORDE);
      orde.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurTools.RANG_ORDE));
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
      familie.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurTools.RANG_FAMILIE));
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

    for (var i = 0; i < taalkolom.length; i++) {
      if (DoosUtils.isNotBlankOrNull(taalkolom[i])
          && DoosUtils.isNotBlankOrNull(veld[i+4])) {
        namen.put(taalkolom[i], veld[i+4]);
      }
    }
  }
}
