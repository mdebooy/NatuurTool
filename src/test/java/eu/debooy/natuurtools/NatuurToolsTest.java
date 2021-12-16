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

import eu.debooy.doosutils.test.BatchTest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class NatuurToolsTest extends BatchTest {
  protected void execute(String[] args) {
    before();
    NatuurTools.main(args);
    after();
  }

  @Test
  public void testLeeg() {
    var args      = new String[] {};

    execute(args);

    assertEquals("Geen tool - fouten", 0, err.size());
  }

  @Test
  public void testCsvNaarJson() {
    var args      = new String[] {"CsvNaarJson"};

    execute(args);

    assertEquals("CsvNaarJson - fouten", 1, err.size());
  }

  @Test
  public void testDbNaarJson() {
    var args      = new String[] {"DbNaarJson"};

    execute(args);

    assertEquals("DbNaarJson - fouten", 1, err.size());
  }

  @Test
  public void testIocNamen() {
    var args      = new String[] {"IocNamen"};

    execute(args);

    assertEquals("IocNamen - fouten", 1, err.size());
  }

  @Test
  public void testOnbestaand() {
    var args      = new String[] {"onbestaand"};

    execute(args);

    assertEquals("onbestaand - fouten", 1, err.size());
  }

  @Test
  public void testTaxaImport() {
    var args      = new String[] {"TaxaImport"};

    execute(args);

    assertEquals("TaxaImport - fouten", 1, err.size());
  }

  @Test
  public void testTaxonomie() {
    var args      = new String[] {"Taxonomie"};

    execute(args);

    assertEquals("Taxonomie - fouten", 1, err.size());
  }
}
