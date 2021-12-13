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
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import static eu.debooy.natuurtools.NatuurTools.QRY_RANG;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final  JSONParser            parser  = new JSONParser();
  private static final  List<String>          rangen  = new ArrayList<>();
  private static final  Map<String, Integer>  totalen = new HashMap<>();

  private static  EntityManager em;

  protected DbNaarJson() {}

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
  }

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_DBNAARJSON)
                           .setClassloader(DbNaarJson.class.getClassLoader())
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    em  = NatuurTools.getEntityManager(
              paramBundle.getString(NatuurTools.PAR_DBUSER),
              paramBundle.getString(NatuurTools.PAR_DBURL),
              paramBundle.getString(NatuurTools.PAR_WACHTWOORD));

    var taxoninfo = paramBundle.getString(NatuurTools.PAR_TAXAROOT).split(",");
    getRangen();

    var namen   = new JSONObject();
    var parent  = NatuurTools.getTaxon(taxoninfo[1], em);
    var root    = new JSONObject();

    root.put(NatuurTools.KEY_LATIJN, parent.getLatijnsenaam());
    root.put(NatuurTools.KEY_RANG, parent.getRang());
    root.put(NatuurTools.KEY_SEQ, parent.getVolgnummer());
    parent.getTaxonnamen().forEach(naam -> namen.put(naam.getTaal(),
                                                     naam.getNaam()));
    if (!namen.isEmpty()) {
      root.put(NatuurTools.KEY_NAMEN, namen);
    }

    var subRangen = verwerkKinderen(parent.getTaxonId());
    if (!subRangen.isEmpty()) {
      root.put(NatuurTools.KEY_SUBRANGEN, subRangen);
    }

    NatuurTools.writeJson(paramBundle.getBestand(PAR_JSONBESTAND,
                                                 BestandConstants.EXT_JSON),
                          root, paramBundle.getString(PAR_CHARSETUIT));

    em.close();

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
    List<RangDto> ranglijst = em.createQuery(QRY_RANG).getResultList();

    ranglijst.forEach(rang -> {
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
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
