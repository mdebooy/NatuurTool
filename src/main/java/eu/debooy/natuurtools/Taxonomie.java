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
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.TekstBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import static eu.debooy.natuur.domain.TaxonDto.PAR_OUDER;
import static eu.debooy.natuur.domain.TaxonDto.QRY_KINDEREN;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;


/**
 * @author Marco de Booij
 */
public class Taxonomie extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  String  QRY_ROOT  =
      "select t from TaxonDto t where t.parentId is null"
          + " order by t.rang, t.volgnummer";

  private static final  Map<String, String>   prefix  = new HashMap<>();
  private static final  List<String>          rangen  = new ArrayList<>();
  private static final  List<String>          talen   = new ArrayList<>();

  private static  EntityManager em;
  private static  TekstBestand  texBestand;

  private Taxonomie() {}

  public static void execute(String[] args) {
    Banner.printDoosBanner(resourceBundle.getString("banner.taxonomie"));

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

    if (parameters.containsKey(NatuurTools.PAR_RANGEN)) {
      rangen.addAll(Arrays.asList(parameters.get(NatuurTools.PAR_RANGEN)
                                            .split(",")));
    }

    if (parameters.containsKey(NatuurTools.PAR_TALEN)) {
      talen.addAll(Arrays.asList(parameters.get(NatuurTools.PAR_TALEN)
                                           .split(",")));
      Collections.sort(talen);
    }

    getRangen();

    try {
      texBestand  = new TekstBestand.Builder()
                                    .setLezen(false)
                                    .setBestand(parameters.get(PAR_UITVOERDIR)
                                      + parameters.get(PAR_TEXBESTAND)
                                      + EXT_TEX)
                                    .setCharset("UTF-8")
                                    .build();

      List<TaxonDto>  root  = em.createQuery(QRY_ROOT).getResultList();
      for (TaxonDto taxon : root) {
        verwerkKinderen(taxon);
      }

    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    } finally {
      if (null != texBestand) {
        try {
          texBestand.close();
        } catch (BestandException e) {
          DoosUtils.foutNaarScherm("tex: " + e.getLocalizedMessage());
        }
      }
    }

  }

  private static void getRangen() {
    List<RangDto> ranglijst =
        em.createQuery("select r from RangDto r order by r.niveau")
          .getResultList();

    ranglijst.forEach(rang ->
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue())));
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar Taxonomie "
        + getMelding(LBL_OPTIE) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), PAR_TEXBESTAND,
              getMelding(LBL_TEXBESTAND)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBURL,
              resourceBundle.getString(NatuurTools.LBL_DBURL)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBUSER,
              resourceBundle.getString(NatuurTools.LBL_DBUSER)), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBURL, 14),
                         resourceBundle.getString(NatuurTools.HLP_DBURL), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBUSER, 14),
                         resourceBundle.getString(NatuurTools.HLP_DBUSER), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_RANGEN, 14),
                         resourceBundle.getString(NatuurTools.HLP_RANGEN),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_TAAL, 14),
        MessageFormat.format(resourceBundle.getString(NatuurTools.HLP_TAAL),
                             Locale.getDefault().getLanguage()), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TALEN, 14),
                         resourceBundle.getString(NatuurTools.HLP_INCLUDETALEN),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_TEXBESTAND, 14),
                         getMelding(HLP_TEXBESTAND), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_UITVOERDIR, 14),
                         getMelding(HLP_UITVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_WACHTWOORD, 14),
                         resourceBundle.getString(NatuurTools.HLP_WACHTWOORD),
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             PAR_TEXBESTAND + ", " + NatuurTools.PAR_DBURL,
                             NatuurTools.PAR_DBUSER), 80);
    DoosUtils.naarScherm();
  }

  private static boolean setParameters(String[] args) {
    var           arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          NatuurTools.PAR_RANGEN,
                                          PAR_TAAL,
                                          NatuurTools.PAR_TALEN,
                                          PAR_TEXBESTAND,
                                          PAR_UITVOERDIR,
                                          NatuurTools.PAR_WACHTWOORD});
    arguments.setVerplicht(new String[] {PAR_TEXBESTAND,
                                         NatuurTools.PAR_DBURL,
                                         NatuurTools.PAR_DBUSER});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    parameters  = new HashMap<>();

    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setParameter(arguments, NatuurTools.PAR_RANGEN);
    setParameter(arguments, PAR_TAAL, Locale.getDefault().getLanguage());
    NatuurTools.setTalenParameter(arguments, parameters);
    setParameter(arguments, PAR_TEXBESTAND);
    setDirParameter(arguments, PAR_UITVOERDIR);
    setParameter(arguments, NatuurTools.PAR_WACHTWOORD);

    if (DoosUtils.nullToEmpty(parameters.get(PAR_TEXBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              getMelding(ERR_BEVATDIRECTORY), PAR_TEXBESTAND));
    }

    if (fouten.isEmpty()) {
      return true;
    }

    help();
    printFouten(fouten);

    return false;
  }

  private static void verwerkKinderen(TaxonDto parent) throws BestandException {
    if (rangen.isEmpty()
        || rangen.contains(parent.getRang())) {
      DoosUtils.naarScherm(prefix.get(parent.getRang()) + " " + parent.getRang()
                            + " " + parent.getLatijnsenaam());
      var     regel   = new StringBuilder();
      String  naam    = parent.getNaam(parameters.get(PAR_TAAL));
      String  latijn  = parent.getLatijnsenaam();
      regel.append("\\taxon{")
           .append(parent.getRang()).append("}{")
           .append(latijn).append("}{");
      if (!naam.equals(latijn)) {
        regel.append(naam);
      }
      regel.append("}{");
      parent.getTaxonnamen()
            .stream()
            .filter(taxonnaam -> (talen.contains(taxonnaam.getTaal())
                                  || talen.isEmpty()))
            .forEachOrdered(taxonnaam -> regel.append("\\naam{")
                                              .append(taxonnaam.getTaal())
                                              .append("}{")
                                              .append(taxonnaam.getNaam())
                                              .append("}"));
      texBestand.write(regel.append("}").toString());
    }

    var query = em.createNamedQuery(QRY_KINDEREN);
    query.setParameter(PAR_OUDER, parent.getTaxonId());

    List<TaxonDto>  taxa  = query.getResultList();
    for (TaxonDto taxon : taxa) {
      verwerkKinderen(taxon);
    }
  }
}
