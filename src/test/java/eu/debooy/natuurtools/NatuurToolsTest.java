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
import eu.debooy.doosutils.test.VangOutEnErr;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class NatuurToolsTest extends BatchTest {
  @Test
  public void testLeeg() {
    String[]  args      = new String[] {};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("Geen tool - helptekst", 93, out.size());
    assertEquals("Geen tool - fouten", 0, err.size());
  }

  @Test
  public void testDbNaarJson() {
    String[]  args      = new String[] {"DbNaarJson"};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("DbNaarJson - helptekst", 26, out.size());
    assertEquals("DbNaarJson - fouten", 1, err.size());
  }

  @Test
  public void testCsvNaarJson() {
    String[]  args      = new String[] {"CsvNaarJson"};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("CsvNaarJson - helptekst", 31, out.size());
    assertEquals("CsvNaarJson - fouten", 1, err.size());
  }

  @Test
  public void testIocNamen() {
    String[]  args      = new String[] {"IocNamen"};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("IocNamen - helptekst", 27, out.size());
    assertEquals("IocNamen - fouten", 1, err.size());
  }

  @Test
  public void testOnbestaand() {
    String[]  args      = new String[] {"onbestaand"};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("onbestaand - helptekst", 93, out.size());
    assertEquals("onbestaand - fouten", 1, err.size());
  }

  @Test
  public void testTaxaNamenImport() {
    String[]  args      = new String[] {"TaxaImport"};

    VangOutEnErr.execute(NatuurTools.class, "main", args, out, err);

    assertEquals("TaxaImport - helptekst", 31, out.size());
    assertEquals("TaxaImport - fouten", 1, err.size());
  }
}
