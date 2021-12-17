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

  private void testTool(String[] args, String tekst, int fouten) {
    execute(args);

    assertEquals(tekst, fouten, err.size());
  }

  @Test
  public void testLeeg() {
    testTool(new String[] {}, "Geen tool - fouten", 0);
  }

  @Test
  public void testCsvNaarJson() {
    testTool(new String[] {"CsvNaarJson"}, "CsvNaarJson - fouten", 1);
  }

  @Test
  public void testDbNaarJson() {
    testTool(new String[] {"DbNaarJson"}, "DbNaarJson - fouten", 1);
  }

  @Test
  public void testIocNamen() {
    testTool(new String[] {"IocNamen"}, "IocNamen - fouten", 1);
  }

  @Test
  public void testOnbestaand() {
    testTool(new String[] {"onbestaand"}, "onbestaand - fouten", 1);
  }

  @Test
  public void testTaxaImport() {
    testTool(new String[] {"TaxaImport"}, "TaxaImport - fouten", 1);
  }

  @Test
  public void testTaxonomie() {
    testTool(new String[] {"Taxonomie"}, "Taxonomie - fouten", 1);
  }
}
