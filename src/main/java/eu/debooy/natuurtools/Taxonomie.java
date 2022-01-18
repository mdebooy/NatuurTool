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
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;


/**
 * @author Marco de Booij
 */
public class Taxonomie extends Batchjob {
  private static final  String  QRY_ROOT  =
      "select t from TaxonDto t where t.parentId is null"
          + " order by t.rang, t.volgnummer";

  private static final  Map<String, String> prefix  = new HashMap<>();
  private static final  List<String>        rangen  = new ArrayList<>();
  private static final  List<String>        talen   = new ArrayList<>();

  private static  EntityManager em;
  private static  TekstBestand  texBestand;

  protected Taxonomie() {}

  public static void execute(String[] args) {
    setParameterBundle(new ParameterBundle.Builder()
                           .setBaseName(NatuurTools.TOOL_TAXONOMIE)
                           .setClassloader(Taxonomie.class.getClassLoader())
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

    em  = NatuurTools.getEntityManager(
                paramBundle.getString(NatuurTools.PAR_DBUSER),
                paramBundle.getString(NatuurTools.PAR_DBURL),
                paramBundle.getString(NatuurTools.PAR_WACHTWOORD));

    getRangen();

    try {
      texBestand  = new TekstBestand.Builder()
                            .setLezen(false)
                            .setBestand(
                                paramBundle
                                    .getBestand(PAR_TEXBESTAND,
                                                BestandConstants.EXT_TEX))
                            .setCharset(BestandConstants.UTF8)
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

    em.close();
  }

  private static void getRangen() {
    List<RangDto> ranglijst = em.createQuery(NatuurTools.QRY_RANG)
                                .getResultList();

    ranglijst.forEach(rang ->
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue())));
  }

  private static void verwerkKinderen(TaxonDto parent) throws BestandException {
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
      verwerkKinderen(taxon);
    }
  }
}
