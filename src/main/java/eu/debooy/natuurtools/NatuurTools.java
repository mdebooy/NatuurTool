/**
 * Copyright (c) 2020 Marco de Booij
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
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.TaxonDto;
import static eu.debooy.natuur.domain.TaxonDto.PAR_LATIJNSENAAM;
import static eu.debooy.natuur.domain.TaxonDto.QRY_LATIJNSENAAM;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class NatuurTools extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  String  KEY_LATIJN    = "latijn";
  protected static final  String  KEY_NAMEN     = "namen";
  protected static final  String  KEY_RANG      = "rang";
  protected static final  String  KEY_SEQ       = "seq";
  protected static final  String  KEY_SOORTEN   = "soorten";
  protected static final  String  KEY_SUBRANGEN = "subrangen";
  protected static final  String  KEY_TAXA      = "taxa";

  protected static final  String  LBL_CSVBESTAND      = "label.csvbestand";
  protected static final  String  LBL_DBURL           = "label.dburl";
  protected static final  String  LBL_DBUSER          = "label.dbuser";
  protected static final  String  LBL_IOCBESTAND      = "label.iocbestand";
  protected static final  String  LBL_JSONBESTAND     = "label.jsonbestand";
  protected static final  String  LBL_RANGEN          = "label.rangen";
  protected static final  String  LBL_SOORTENONBEKEND = "label.soortenonbekend";
  protected static final  String  LBL_TALEN           = "label.talen";
  protected static final  String  LBL_TAXAROOT        = "label.taxaroot";
  protected static final  String  LBL_WACHTWOORD      = "label.wachtwoord";

  protected static final  String  MSG_AANMAKEN        = "msg.aanmaken";
  protected static final  String  MSG_AANTALSOORTEN   = "msg.aantalsoorten";
  protected static final  String  MSG_AANTALONBEKEND  = "msg.aantalonbekend";
  protected static final  String  MSG_HERNUMMER       = "msg.hernummer";
  protected static final  String  MSG_HIERARCHIE      = "msg.hierarchie";
  protected static final  String  MSG_LIJNEN          = "msg.lijnen";
  protected static final  String  MSG_NIEUW           = "msg.nieuw";
  protected static final  String  MSG_ONBEKEND        = "msg.onbekend";
  protected static final  String  MSG_SKIPSTRUCTUUR   = "msg.skipstructuur";
  protected static final  String  MSG_VERSCHIL        = "msg.verschil";
  protected static final  String  MSG_WIJZIGEN        = "msg.wijzigen";
  protected static final  String  MSG_WIJZIGING       = "msg.wijziging";

  protected static final  String  PAR_AANMAAK     = "aanmaak";
  protected static final  String  PAR_BEHOUD      = "behoud";
  protected static final  String  PAR_CSVBESTAND  = "csvbestand";
  protected static final  String  PAR_IOCBESTAND  = "iocbestand";
  protected static final  String  PAR_DBURL       = "dburl";
  protected static final  String  PAR_DBUSER      = "dbuser";
  protected static final  String  PAR_HERNUMMER   = "hernummer";
  protected static final  String  PAR_JSON        = "json";
  protected static final  String  PAR_RANGEN      = "rangen";
  protected static final  String  PAR_TALEN       = "talen";
  protected static final  String  PAR_TAXAROOT    = "taxaroot";
  protected static final  String  PAR_WACHTWOORD  = "wachtwoord";

  protected static final  String  QRY_RANG  =
      "select r from RangDto r order by r.niveau";

  protected static final  String  RANG_FAMILIE    = "fa";
  protected static final  String  RANG_GESLACHT   = "ge";
  protected static final  String  RANG_KLASSE     = "kl";
  protected static final  String  RANG_ORDE       = "or";
  protected static final  String  RANG_ONDERSOORT = "oso";
  protected static final  String  RANG_SOORT      = "so";

  protected static final  String  TOOL_DBNAARJSON   = "DbNaarJson";
  protected static final  String  TOOL_CSVNAARJSON  = "CsvNaarJson";
  protected static final  String  TOOL_IOCCHECK     = "IocCheck";
  protected static final  String  TOOL_IOCNAMEN     = "IocNamen";
  protected static final  String  TOOL_TAXAIMPORT   = "TaxaImport";
  protected static final  String  TOOL_TAXONOMIE    = "Taxonomie";

  protected static final  String  TXT_BANNER  = "help.natuurtools";

  protected static final  List<String>  tools =
      Arrays.asList(TOOL_DBNAARJSON, TOOL_CSVNAARJSON, TOOL_IOCCHECK,
                    TOOL_IOCNAMEN, TOOL_TAXAIMPORT, TOOL_TAXONOMIE);

  protected NatuurTools() {}

  protected static EntityManager getEntityManager(String dbuser, String dburl) {
    return getEntityManager(dbuser, dburl, null);
  }

  protected static EntityManager getEntityManager(String dbuser, String dburl,
                                                  String wachtwoord) {
    var props = new Properties();
    if (null == wachtwoord) {
      wachtwoord  =
        DoosUtils.getWachtwoord(MessageFormat.format(
            resourceBundle.getString(LBL_WACHTWOORD),
            dbuser, dburl.split("/")[1]));
    }

    props.put("openjpa.ConnectionURL",      "jdbc:postgresql://" + dburl);
    props.put("openjpa.ConnectionUserName", dbuser);
    props.put("openjpa.ConnectionPassword", wachtwoord);

    return Persistence.createEntityManagerFactory("natuur", props)
                      .createEntityManager();
  }

  protected static TaxonDto getTaxon(String latijnsenaam, EntityManager em) {
    var query = em.createNamedQuery(QRY_LATIJNSENAAM);
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
    }

    return resultaat;
  }

  public static void help() {
    tools.forEach(tool -> {
      var parameterBundle = new ParameterBundle.Builder()
                           .setBaseName(tool)
                           .setClassloader(NatuurTools.class.getClassLoader())
                           .build();
      parameterBundle.help();
      DoosUtils.naarScherm(DoosUtils.stringMetLengte("_", 80, "_"));
      DoosUtils.naarScherm();
    });
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printDoosBanner(resourceBundle.getString(TXT_BANNER));
      help();
      return;
    }

    String    commando      = args[0];
    String[]  commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    switch (commando.toLowerCase()) {
      case "dbnaarjson":
        DbNaarJson.execute(commandoArgs);
        break;
      case "csvnaarjson":
        CsvNaarJson.execute(commandoArgs);
        break;
      case "iocnamen":
        IocNamen.execute(commandoArgs);
        break;
      case "ioccheck":
        IocCheck.execute(commandoArgs);
        break;
      case "taxaimport":
        TaxaImport.execute(commandoArgs);
        break;
      case "taxonomie":
        Taxonomie.execute(commandoArgs);
        break;
      default:
        Banner.printDoosBanner(resourceBundle.getString(TXT_BANNER));
        help();
        DoosUtils.foutNaarScherm(
            MessageFormat.format(getMelding(ERR_TOOLONBEKEND), commando));
        DoosUtils.naarScherm();
        break;
    }
  }

  protected static void writeJson(String bestand, JSONObject taxa,
                                  String charset) {
    try (var jsonBestand  =
          new JsonBestand.Builder().setBestand(bestand)
                                   .setCharset(charset)
                                   .setLezen(false)
                                   .setPrettify(true).build()) {
      jsonBestand.write(taxa);
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }
}
