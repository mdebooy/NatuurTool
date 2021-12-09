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
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.DetailDto;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class IocCheck extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static  EntityManager em;

  private static final  String  QUERY =
      "select d from DetailDto d where d.parentLatijnsenaam='Aves' "
          + "and d.rang='so' order by d.volgnummer";

  protected static  List<String>  latijnsenamen = new ArrayList<>();

  protected IocCheck() {}

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName("IocCheck")
                           .setClassloader(IocCheck.class.getClassLoader())
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    try (var jsonBestand =
          new JsonBestand.Builder()
                         .setBestand(paramBundle.getBestand("json", EXT_JSON))
                         .build()) {
      JSONArray taxa  = (JSONArray) jsonBestand.get("taxa");
      taxa.forEach(blad -> {
        zoekSoorten((JSONObject) blad);
      });
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    getEntityManager();
    var onbekend  = 0;
    for (var detail : em.createQuery(QUERY).getResultList()) {
      if (!latijnsenamen.contains(((DetailDto) detail).getLatijnsenaam())) {
        var volgnummer  =
            DoosUtils.stringMetLengte(((DetailDto) detail).getVolgnummer()
                                                          .toString(), 8);
        if (onbekend == 0) {
          DoosUtils.naarScherm();
          DoosUtils.naarScherm(
              resourceBundle.getString(NatuurTools.LBL_SOORTENONBEKEND));
        }
        DoosUtils.naarScherm(volgnummer + " "
                              + ((DetailDto) detail).getLatijnsenaam());
        onbekend++;
      }
    }

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_AANTALONBEKEND),
            onbekend));
    DoosUtils.naarScherm(
        MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_AANTALSOORTEN),
            latijnsenamen.size()));
  }

  private static void getEntityManager() {
    if (paramBundle.containsParameter(NatuurTools.PAR_WACHTWOORD)) {
      em  = NatuurTools.getEntityManager(
                paramBundle.getString(NatuurTools.PAR_DBUSER),
                paramBundle.getString(NatuurTools.PAR_DBURL),
                paramBundle.getString(NatuurTools.PAR_WACHTWOORD));
    } else {
      em  = NatuurTools.getEntityManager(
                paramBundle.getString(NatuurTools.PAR_DBUSER),
                paramBundle.getString(NatuurTools.PAR_DBURL));
    }
  }

  private static void setLatijnsenamen(JSONObject tree) {
    tree.keySet()
        .stream()
        .filter(sleutel -> "latijn".equalsIgnoreCase(sleutel.toString()))
        .forEachOrdered(sleutel ->
            latijnsenamen.add(tree.get(sleutel).toString()));
  }

  private static void zoekSoorten(JSONObject tree) {
    tree.keySet()
        .stream()
        .filter(sleutel -> (tree.get(sleutel) instanceof JSONArray))
        .forEachOrdered(sleutel -> {
      if ("soorten".equals(sleutel)) {
        ((JSONArray) tree.get(sleutel)).forEach(blad ->
            setLatijnsenamen((JSONObject) blad));
      } else {
        ((JSONArray) tree.get(sleutel)).forEach(blad ->
            zoekSoorten((JSONObject) blad));
      }
    });
  }
}
