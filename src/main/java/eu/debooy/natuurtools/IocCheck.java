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

import eu.debooy.doos.domain.TaalDto;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.BestandConstants;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.domain.DetailDto;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class IocCheck extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  String  PAR_KLASSE  = "klasse";

  private static final  String  QUERY =
      "select d from DetailDto d where d.parentLatijnsenaam='%s' "
          + "and d.rang='so' order by d.volgnummer";

  protected static  List<String>  latijnsenamen = new ArrayList<>();

  protected IocCheck() {}

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_IOCCHECK)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    try (var jsonBestand =
          new JsonBestand.Builder()
                         .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_JSON,
                                                   BestandConstants.EXT_JSON))
                         .build()) {
      for (Object taxa :
              (JSONArray) jsonBestand.get(NatuurTools.KEY_SUBRANGEN)) {
        zoekSoorten((JSONObject) taxa);
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      return;
    }

    var onbekend  = 0;
    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var em  = dbConn.getEntityManager();

      var taal  = paramBundle.getString(PAR_TAAL);
      if (taal.length() == 2) {
        taal  = ((TaalDto)  em.createNamedQuery(TaalDto.QRY_TAAL_ISO6391)
                              .setParameter(TaalDto.PAR_ISO6391,
                                            paramBundle.getString(PAR_TAAL))
                              .getSingleResult()).getIso6392t();
      }

      for (var detail : em.createQuery(String.format(
                                          QUERY,
                                          paramBundle.getString(PAR_KLASSE)))
                          .getResultList()) {
        var detailDto = (DetailDto) detail;
        if (!latijnsenamen.contains(detailDto.getLatijnsenaam())) {
          if (onbekend == 0) {
            DoosUtils.naarScherm();
            DoosUtils.naarScherm(MessageFormat.format(
                resourceBundle.getString(NatuurTools.LBL_SOORTENONBEKEND),
                paramBundle.getString(PAR_KLASSE)));
          }
          DoosUtils.naarScherm(String.format("%8d %s - %s",
                                             detailDto.getVolgnummer(),
                                             detailDto.getLatijnsenaam(),
                                             detailDto.getNaam(taal)));
          onbekend++;
        }
      }
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
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
    klaar();
  }

  private static void zoekSoorten(JSONObject tree) {
    var boom  = tree.keySet();

    if (boom.contains(NatuurTools.KEY_RANG)
        && tree.get(NatuurTools.KEY_RANG)
               .toString().equals(NatuurConstants.RANG_SOORT)) {
      latijnsenamen.add(tree.get(NatuurTools.KEY_LATIJN).toString());
      return;
    }

    if (boom.contains(NatuurTools.KEY_SUBRANGEN)) {
      ((JSONArray) tree.get(NatuurTools.KEY_SUBRANGEN)).forEach(tak ->
          zoekSoorten((JSONObject) tak));
    }
  }
}
