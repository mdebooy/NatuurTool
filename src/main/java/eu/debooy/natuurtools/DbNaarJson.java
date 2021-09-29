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

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import static eu.debooy.doosutils.Batchjob.setBestandParameter;
import static eu.debooy.doosutils.Batchjob.setDirParameter;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class DbNaarJson extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  JSONParser              parser      = new JSONParser();
  private static final  List<String>            rangen      = new ArrayList<>();
  private static final  Map<String, Integer>    totalen     = new HashMap<>();

  private static  EntityManager em;

  private DbNaarJson() {}

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
  }

  public static void execute(String[] args) {
    Banner.printDoosBanner(resourceBundle.getString("banner.dbnaarjson"));

    if (!setParameters(args)) {
      return;
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

    String[]  taxoninfo = parameters.get(NatuurTools.PAR_TAXAROOT)
                                        .split(",");
    getRangen();

    var       namen     = new JSONObject();
    TaxonDto  parent    = NatuurTools.getTaxon(taxoninfo[1], em);
    var       root      = new JSONObject();

    root.put(NatuurTools.KEY_LATIJN, parent.getLatijnsenaam());
    root.put(NatuurTools.KEY_RANG, parent.getRang());
    root.put(NatuurTools.KEY_SEQ, parent.getVolgnummer());
    parent.getTaxonnamen().forEach(naam -> namen.put(naam.getTaal(),
                                                     naam.getNaam()));
    if (!namen.isEmpty()) {
      root.put(NatuurTools.KEY_NAMEN, namen);
    }

    JSONArray subRangen = verwerkKinderen(parent.getTaxonId());
    if (!subRangen.isEmpty()) {
      root.put(NatuurTools.KEY_SUBRANGEN, subRangen);
    }

    NatuurTools.writeJson(parameters.get(PAR_UITVOERDIR)
                           + parameters.get(PAR_JSONBESTAND) + EXT_JSON,
                          root,
                          parameters.get(PAR_CHARSETUIT));

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

  private static void getRangen() {
    List<RangDto> ranglijst =
        em.createQuery("select r from RangDto r order by r.niveau")
          .getResultList();

    ranglijst.forEach(rang -> {
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar DbNaarJson "
        + getMelding(LBL_OPTIE) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBURL,
              resourceBundle.getString(NatuurTools.LBL_DBURL)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBUSER,
              resourceBundle.getString(NatuurTools.LBL_DBUSER)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), PAR_JSONBESTAND,
              resourceBundle.getString(NatuurTools.LBL_JSONBESTAND)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_TAXAROOT,
              resourceBundle.getString(NatuurTools.LBL_TAXAROOT)), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETUIT, 12),
        MessageFormat.format(getMelding(HLP_CHARSETUIT),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBURL, 12),
                         resourceBundle.getString(NatuurTools.HLP_DBURL), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBUSER, 12),
                         resourceBundle.getString(NatuurTools.HLP_DBUSER), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_JSONBESTAND, 12),
                         resourceBundle.getString(NatuurTools.HLP_IOCJSON),
                         80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TAXAROOT, 12),
                         resourceBundle.getString(NatuurTools.HLP_TAXAROOT),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_UITVOERDIR, 12),
                         getMelding(HLP_UITVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_WACHTWOORD, 12),
                         resourceBundle.getString(NatuurTools.HLP_WACHTWOORD),
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             NatuurTools.PAR_DBURL + ", "
                             + NatuurTools.PAR_DBUSER + ", " +
                             PAR_JSONBESTAND, NatuurTools.PAR_TAXAROOT),
                             80);
    DoosUtils.naarScherm();
  }

  private static boolean setParameters(String[] args) {
    var           arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {PAR_CHARSETUIT,
                                          NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          PAR_JSONBESTAND,
                                          NatuurTools.PAR_TAXAROOT,
                                          PAR_UITVOERDIR,
                                          NatuurTools.PAR_WACHTWOORD});
    arguments.setVerplicht(new String[] {NatuurTools.PAR_DBURL,
                                         NatuurTools.PAR_DBUSER,
                                         PAR_JSONBESTAND,
                                         NatuurTools.PAR_TAXAROOT});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    parameters  = new HashMap<>();

    setParameter(arguments, PAR_CHARSETUIT, Charset.defaultCharset().name());
    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setBestandParameter(arguments, PAR_JSONBESTAND, EXT_JSON);
    setParameter(arguments, NatuurTools.PAR_TAXAROOT);
    setDirParameter(arguments, PAR_UITVOERDIR, getParameter(PAR_INVOERDIR));
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

  private static JSONArray verwerkKinderen(Long parentId) {
    var jsonRangen  = new JSONArray();
    var query       = em.createNamedQuery(TaxonDto.QRY_KINDEREN);
    query.setParameter(TaxonDto.PAR_OUDER, parentId);
    List<TaxonDto>  taxa = new ArrayList<>();
    try {
      taxa  = query.getResultList();
    } catch (NoResultException e) {
      // taxa is nog steeds een lege List.
    }

    taxa.forEach(taxon -> {
      addRang(taxon.getRang());
      var jsonRang  = new JSONObject();
      var namen     = new JSONObject();
      jsonRang.put(NatuurTools.KEY_LATIJN, taxon.getLatijnsenaam());
      jsonRang.put(NatuurTools.KEY_RANG, taxon.getRang());
      jsonRang.put(NatuurTools.KEY_SEQ, taxon.getVolgnummer());
      taxon.getTaxonnamen().forEach(naam -> namen.put(naam.getTaal(),
                                                      naam.getNaam()));
      if (!namen.isEmpty()) {
        jsonRang.put(NatuurTools.KEY_NAMEN, namen);
      }
      JSONArray subRangen = verwerkKinderen(taxon.getTaxonId());
      if (!subRangen.isEmpty()) {
        jsonRang.put(NatuurTools.KEY_SUBRANGEN, subRangen);
      }

      try {
        jsonRangen.add(parser.parse(jsonRang.toString()));
      } catch (ParseException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      }
    });

    return jsonRangen;
  }
}
