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

import eu.debooy.doosutils.IParameterBundleValidator;
import eu.debooy.doosutils.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author Marco de Booij
 */
public class ParameterAfwezig implements IParameterBundleValidator {
  private boolean isAfwezig(String bron, String doel,
                            Map<String, Parameter> params,
                            List<String> argumenten) {
    return params.containsKey(bron) && params.containsKey(doel)
            && argumenten.contains(bron) && !argumenten.contains(doel);
  }

  private void setAfwezig(String bron, String doel,
                          Map<String, Parameter> params) {
    params.get(doel).setWaarde(params.get(bron).getWaarde());
  }

  @Override
  public List<String> valideer(Map<String, Parameter> params,
                               List<String> argumenten) {
    List<String>  fouten  = new ArrayList<>();

    if (isAfwezig(NatuurTools.PAR_IOCBESTAND, NatuurTools.PAR_JSON,
                  params, argumenten)) {
      setAfwezig(NatuurTools.PAR_IOCBESTAND, NatuurTools.PAR_JSON, params);
    }
    if (isAfwezig(NatuurTools.PAR_CSVBESTAND, NatuurTools.PAR_JSON,
                  params, argumenten)) {
      setAfwezig(NatuurTools.PAR_CSVBESTAND, NatuurTools.PAR_JSON, params);
    }

    return fouten;
  }
}
