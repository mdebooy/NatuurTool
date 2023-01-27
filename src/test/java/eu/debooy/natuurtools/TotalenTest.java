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

import static org.junit.Assert.assertEquals;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class TotalenTest {
  private static final  Integer EEN       = 1;
  private static final  Integer NUL       = 0;
  private static final  Integer TIEN      = 10;
  private static final  String  TOSTRINGA =
          ":         0         0         0";
  private static final  String  TOSTRINGB =
          "Totalen   :         0         0         0";
  private static final  String  TOTALENA  = "Totalen";
  private static final  String  TOTALENB  = "Een andere";
  private static final  Integer TWEE      = 2;

  @Test
  public void testAddAantal() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getAantal());

    totalen.addAantal();

    assertEquals(EEN, totalen.getAantal());

    totalen.addAantal();

    assertEquals(TWEE, totalen.getAantal());
  }

  @Test
  public void testAddNieuw() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getNieuw());

    totalen.addNieuw();

    assertEquals(EEN, totalen.getNieuw());

    totalen.addNieuw();

    assertEquals(TWEE, totalen.getNieuw());
  }

  @Test
  public void testAddUpdate() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getUpdate());

    totalen.addUpdate();

    assertEquals(EEN, totalen.getUpdate());

    totalen.addUpdate();

    assertEquals(TWEE, totalen.getUpdate());
  }

  @Test
  public void testGetAantal() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getAantal());
  }

  @Test
  public void testGetLengte() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getLengte());
  }

  @Test
  public void testGetNieuw() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getNieuw());
  }

  @Test
  public void testGetUpdate() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getUpdate());
  }

  @Test
  public void testInit1() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getAantal());
    assertEquals(TOTALENA, totalen.getLabel());
    assertEquals(NUL, totalen.getLengte());
    assertEquals(NUL, totalen.getNieuw());
    assertEquals(TOSTRINGA, totalen.toString());
    assertEquals(NUL, totalen.getUpdate());
  }

  @Test
  public void testInit2() {
    var totalen = new Totalen(TOTALENA, 10);

    assertEquals(NUL, totalen.getAantal());
    assertEquals(TOTALENA, totalen.getLabel());
    assertEquals(TIEN, totalen.getLengte());
    assertEquals(NUL, totalen.getNieuw());
    assertEquals(TOSTRINGB, totalen.toString());
    assertEquals(NUL, totalen.getUpdate());
  }

  @Test
  public void testSetLabel() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(TOTALENA, totalen.getLabel());

    totalen.setLabel(TOTALENB);

    assertEquals(TOTALENB, totalen.getLabel());
  }

  @Test
  public void testSetLengte() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(NUL, totalen.getLengte());

    totalen.setLengte(TWEE);

    assertEquals(TWEE, totalen.getLengte());
  }

  @Test
  public void testToString() {
    var totalen = new Totalen(TOTALENA);

    assertEquals(TOSTRINGA, totalen.toString());

    totalen.setLengte(TIEN);

    assertEquals(TOSTRINGB, totalen.toString());
  }
}
