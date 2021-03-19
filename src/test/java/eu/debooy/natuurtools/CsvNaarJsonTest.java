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
import java.io.File;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class CsvNaarJsonTest extends BatchTest {
  protected static final  ClassLoader CLASSLOADER =
      CsvNaarJsonTest.class.getClassLoader();

  private static final  String  BST_CSV         = "MultilingIOC.csv";
  private static final  String  BST_JSON        = "MultilingIOC.json";
  private static final  String  PAR_CSVBESTAND  = "csvbestand";
  private static final  String  PAR_JSONBESTAND = "jsonbestand";

  @Test
  public void testCsvBestandMetDirectory() {
    String[]  args      = new String[] {"--" + PAR_CSVBESTAND
                                         + "=" + TEMP + File.separator
                                         + BST_CSV,
                                        "--invoerdir=" + TEMP,
                                        "--" + NatuurTools.PAR_DBURL + "=url",
                                        "--" + NatuurTools.PAR_DBUSER + "=user",
                                        "--" + NatuurTools.PAR_TAXAROOT +
                                        "=kl,Aves"};

    VangOutEnErr.execute(CsvNaarJson.class, "execute", args, out, err);

    assertEquals("CsvBestand Met Directory - helptekst", 31, out.size());
    assertEquals("CsvBestand Met Directory - fouten", 1, err.size());
  }

  @Test
  public void testJsonBestandMetDirectory() {
    String[]  args      = new String[] {"--" + PAR_CSVBESTAND
                                         + "=" + BST_CSV,
                                        "--" + PAR_JSONBESTAND
                                         + "=" + TEMP + File.separator
                                         + BST_JSON,
                                        "--invoerdir=" + TEMP,
                                        "--" + NatuurTools.PAR_DBURL + "=url",
                                        "--" + NatuurTools.PAR_DBUSER + "=user",
                                        "--" + NatuurTools.PAR_TAXAROOT +
                                        "=kl,Aves"};

    VangOutEnErr.execute(CsvNaarJson.class, "execute", args, out, err);

    assertEquals("JsonBestand Met Directory - helptekst", 31, out.size());
    assertEquals("JsonBestand Met Directory - fouten", 1, err.size());
  }

  @Test
  public void testLeeg() {
    String[]  args      = new String[] {};

    VangOutEnErr.execute(CsvNaarJson.class, "execute", args, out, err);

    assertEquals("Zonder parameters - helptekst", 31, out.size());
    assertEquals("Zonder parameters - fouten", 1, err.size());
  }
}
