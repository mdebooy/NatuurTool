/**
 * Copyright 2020 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
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
import eu.debooy.doosutils.DoosUtils;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author Marco de Booij
 */
public class NatuurTools {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  String  ERR_BEVATDIRECTORY  = "error.bevatdirectory";
  protected static final  String  ERR_INVALIDPARAMS   = "error.invalid.params";

  protected static final  String  EXT_CSV   = ".csv";
  protected static final  String  EXT_JSON  = ".json";

  protected static final  String  KEY_FAMILIE   = "familie";
  protected static final  String  KEY_FAMILIES  = "families";
  protected static final  String  KEY_ID        = "ID";
  protected static final  String  KEY_LATIJN    = "latijn";
  protected static final  String  KEY_NAMEN     = "namen";
  protected static final  String  KEY_ORDE      = "orde";
  protected static final  String  KEY_SOORTEN   = "soorten";

  protected static final  String  LBL_FOUT        = "label.fout";
  protected static final  String  LBL_WACHTWOORD  = "label.wachtwoord";

  protected static final  String  MSG_FAMILIE   = "msg.familie";
  protected static final  String  MSG_FAMILIES  = "msg.families";
  protected static final  String  MSG_KLAAR     = "msg.klaar";
  protected static final  String  MSG_LIJNEN    = "msg.lijnen";
  protected static final  String  MSG_NIEUW     = "msg.nieuw";
  protected static final  String  MSG_ONBEKEND  = "msg.onbekend";
  protected static final  String  MSG_ORDE      = "msg.orde";
  protected static final  String  MSG_ORDES     = "msg.ordes";
  protected static final  String  MSG_SOORT     = "msg.soort";
  protected static final  String  MSG_SOORTEN   = "msg.soorten";
  protected static final  String  MSG_VERSCHIL  = "msg.verschil";
  protected static final  String  MSG_WIJZIGEN  = "msg.wijzigen";

  protected static final  String  PAR_IOCBESTAND  = "iocbestand";
  protected static final  String  PAR_CHARSETIN   = "charsetin";
  protected static final  String  PAR_CHARSETUIT  = "charsetuit";
  protected static final  String  PAR_DBURL       = "dburl";
  protected static final  String  PAR_DBUSER      = "dbuser";
  protected static final  String  PAR_INVOERDIR   = "invoerdir";
  protected static final  String  PAR_JSONBESTAND = "jsonbestand";
  protected static final  String  PAR_READONLY    = "readonly";
  protected static final  String  PAR_TALEN       = "talen";
  protected static final  String  PAR_UITVOERDIR  = "uitvoerdir";

  private NatuurTools() {}

  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printDoosBanner("Natuur Tools");
      help();
      return;
    }

    String    commando      = args[0];

    String[]  commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    if ("iocnamen".equalsIgnoreCase(commando)) {
      IocNamen.execute(commandoArgs);
      return;
    }
    if ("taxanamenimport".equalsIgnoreCase(commando)) {
      TaxaNamenImport.execute(commandoArgs);
      return;
    }

    Banner.printDoosBanner("Natuur Tools");
    help();
  }

  private static void help() {
    DoosUtils.naarScherm("  IOCNamen        ",
                         resourceBundle.getString("help.iocnamen"), 80);
    DoosUtils.naarScherm("  TaxaNamenImport ",
                         resourceBundle.getString("help.taxanamenimport"), 80);
    DoosUtils.naarScherm();
    IocNamen.help();
    DoosUtils.naarScherm();
    TaxaNamenImport.help();
  }
}
