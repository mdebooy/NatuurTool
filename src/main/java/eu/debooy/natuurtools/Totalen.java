/*
 * Copyright (c) 2023 Marco de Booij
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

import eu.debooy.doosutils.DoosUtils;


/**
 * @author Marco de Booij
 */
public class Totalen {
  private static final  String  FMT_TOTALEN = "%s: %,9d %,9d %,9d";

  private Integer aantal  = 0;
  private String  label;
  private Integer lengte  = 0 ;
  private Integer nieuw   = 0;
  private Integer update  = 0;

  public Totalen(String label) {
    this.label  = label;
  }

  public Totalen(String label, int lengte) {
    this.label  = label;
    this.lengte = lengte;
  }

  public void addAantal() {
    aantal++;
  }

  public void addNieuw() {
    nieuw++;
  }

  public void addUpdate() {
    update++;
  }

  public Integer getAantal() {
    return aantal;
  }

  public Integer getGewijzigd() {
    return update;
  }

  public Integer getLengte() {
    return lengte;
  }

  public Integer getNieuw() {
    return nieuw;
  }

  public void setLabel(String label) {
    this.label  = label;
  }

  public void setLengte(Integer lengte) {
    this.lengte = lengte;
  }

  @Override
  public String toString() {
    return String.format(FMT_TOTALEN,
                         DoosUtils.stringMetLengte(label, lengte, " "),
                         aantal, update, nieuw);
  }
}
