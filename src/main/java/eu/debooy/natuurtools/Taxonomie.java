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
import eu.debooy.doosutils.access.TekstBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Query;


/**
 * @author Marco de Booij
 */
public class Taxonomie extends Batchjob {
  private static final  ClassLoader CLASSLOADER   =
      Taxonomie.class.getClassLoader();
  private static final  String      DEF_TEMPLATE  = "Taxonomie.tex";
  private static final  String      QRY_ROOT      =
      "select t from TaxonDto t where t.parentId is null "
          + "order by t.rang, t.volgnummer";

  private static final  Map<String, String> prefix  = new HashMap<>();
  private static final  List<String>        rangen  = new ArrayList<>();
  private static final  List<String>        talen   = new ArrayList<>();

  protected Taxonomie() {}

  private static TekstBestand bepaalTexInvoer()
      throws BestandException {
    TekstBestand  texInvoer;

    if (paramBundle.containsParameter(NatuurTools.PAR_TEMPLATE)) {
      texInvoer =
          new TekstBestand.Builder()
                          .setBestand(
                              paramBundle.getBestand(NatuurTools.PAR_TEMPLATE))
                          .build();
    } else {
      texInvoer =
          new TekstBestand.Builder()
                          .setBestand(DEF_TEMPLATE)
                          .setClassLoader(CLASSLOADER)
                          .build();
    }

    return texInvoer;
  }

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_TAXONOMIE)
                           .build());

    Banner.printDoosBanner(DoosUtils.nullToEmpty(paramBundle.getBanner()));

    if (!paramBundle.isValid()
        || !paramBundle.setArgs(args)) {
      help();
      printFouten();
      return;
    }

    if (paramBundle.containsParameter(NatuurTools.PAR_RANGEN)) {
      rangen.addAll(Arrays.asList(paramBundle.getString(NatuurTools.PAR_RANGEN)
                                             .split(",")));
    }

    if (paramBundle.containsParameter(NatuurTools.PAR_TALEN)) {
      talen.addAll(Arrays.asList(paramBundle.getString(NatuurTools.PAR_TALEN)
                                            .split(",")));
      Collections.sort(talen);
    }

    try (var dbConn =
            new DbConnection.Builder()
                  .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
                  .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
                  .setWachtwoord(
                      paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
                  .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
                  .build();
        var texBestand =
            new TekstBestand.Builder()
                            .setLezen(false)
                            .setBestand(
                                paramBundle
                                    .getBestand(PAR_TEXBESTAND,
                                                BestandConstants.EXT_TEX))
                            .setCharset(BestandConstants.UTF8)
                            .build()) {
      var em = dbConn.getEntityManager();

      getRangen(em);
      verwerkTemplate(texBestand, em);
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    klaar();
  }

  private static TaxonDto getParent(EntityManager em) {
    var parent  = new TaxonDto();
    if (paramBundle.containsArgument(NatuurTools.PAR_TAXAROOT)) {
      var taxoninfo = paramBundle.getString(NatuurTools.PAR_TAXAROOT)
                                 .split(",");
      parent  = NatuurTools.getTaxon(taxoninfo[1], em);
    }

    return parent;
  }

  private static void getRangen(EntityManager em) {
    List<RangDto> ranglijst = em.createQuery(NatuurTools.QRY_RANG)
                                .getResultList();

    ranglijst.forEach(rang ->
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue())));
  }

  private static Map<String, String> setParams(TaxonDto parent) {
    Map<String, String> params  = new HashMap<>();

    if (paramBundle.containsArgument(NatuurTools.PAR_AUTEUR)) {
      params.put("@Auteur@", paramBundle.getString(NatuurTools.PAR_AUTEUR));
    } else {
      params.put("@Auteur@", "");
    }
    params.put("@Kleur@", paramBundle.getString(NatuurTools.PAR_KLEUR));
    params.put("@Titel@", paramBundle.getString(NatuurTools.PAR_TITEL));
    if (null != parent.getTaxonId()) {
      params.put("@Subject@",
                 String.format("%s (%s)",
                               parent.getTaxonnaam(
                                   paramBundle.getString(NatuurTools.PAR_TAAL))
                                     .getNaam(),
                               parent.getLatijnsenaam()));
      params.put("@Subtitel@",
                 String.format("%s (\\textit{%s})",
                               parent.getTaxonnaam(
                                   paramBundle.getString(NatuurTools.PAR_TAAL))
                                     .getNaam(),
                               parent.getLatijnsenaam()));
    } else {
      params.put("@Subject@", params.get("@Titel@"));
      params.put("@Subtitel@", "");
    }

    return params;
  }

  private static void verwerkKinderen(TaxonDto parent, TekstBestand texBestand,
                                      EntityManager em)
      throws BestandException {
    if (rangen.isEmpty()
        || rangen.contains(parent.getRang())) {
      DoosUtils.naarScherm(prefix.get(parent.getRang()) + " " + parent.getRang()
                            + " " + parent.getLatijnsenaam());
      var regel   = new StringBuilder();
      var naam    = parent.getNaam(paramBundle.getString(PAR_TAAL));
      var latijn  = parent.getLatijnsenaam();
      regel.append("\\taxon{")
           .append(parent.getRang()).append("}{")
           .append(latijn).append("}{");
      if (!naam.equals(latijn)) {
        regel.append(naam);
      }
      regel.append("}{");
      parent.getTaxonnamen()
            .stream().sorted()
            .filter(taxonnaam -> (talen.contains(taxonnaam.getTaal())
                                  || talen.isEmpty()))
            .forEachOrdered(taxonnaam -> regel.append("\\naam{")
                                              .append(taxonnaam.getTaal())
                                              .append("}{")
                                              .append(taxonnaam.getNaam())
                                              .append("}"));
      texBestand.write(regel.append("}").toString());
    }

    var query = em.createNamedQuery(TaxonDto.QRY_KINDEREN);
    query.setParameter(TaxonDto.PAR_OUDER, parent.getTaxonId());

    List<TaxonDto>  taxa  = query.getResultList();
    for (var taxon : taxa) {
      verwerkKinderen(taxon, texBestand, em);
    }
  }

  private static void verwerkTaxa(TekstBestand texBestand, TaxonDto parent,
                                  EntityManager em)
      throws BestandException {
    Query query;
    if (null != parent.getTaxonId()) {
      query = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
      query.setParameter(TaxonDto.PAR_LATIJNSENAAM, parent.getLatijnsenaam());
    } else {
      query = em.createQuery(QRY_ROOT);
    }

    List<TaxonDto>  root  = query.getResultList();
    for (var taxon : root) {
      verwerkKinderen(taxon, texBestand, em);
    }
  }

  private static void verwerkRegel(String regel, Map<String, String> params,
                                   TekstBestand texBestand)
      throws BestandException {
    var afgekeurd = 0;
//    params.entrySet().forEach(action);
    for (var param : params.keySet()) {
      if (regel.contains(param)) {
        if (DoosUtils.isNotBlankOrNull(params.get(param))) {
          regel = regel.replace(param, params.get(param));
        } else {
          afgekeurd++;
        }
      }
    }

    if (afgekeurd == 0) {
      texBestand.write(regel);
    }
  }

  private static void verwerkTemplate(TekstBestand texBestand,
                                      EntityManager em) {
    var                 parent  = getParent(em);
    Map<String, String> params  = setParams(parent);

    try (var texInvoer = bepaalTexInvoer()) {
      while (texInvoer.hasNext()) {
        var regel = texInvoer.next();
        if (regel.equals("%@Include taxa")) {
          verwerkTaxa(texBestand, parent, em);
        } else {
          verwerkRegel(regel, params, texBestand);
        }
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }
}
