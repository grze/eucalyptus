
package com.eucalyptus.sdb;
import edu.ucsb.eucalyptus.msgs.BaseMessage;
import edu.ucsb.eucalyptus.msgs.EucalyptusData;
import java.util.ArrayList;
import com.eucalyptus.component.ComponentId.ComponentMessage;



public class ListDomainsType extends SdbMessage {
  Integer maxNumberOfDomains;
  String nextToken;
  public ListDomainsType() {  }
}
public class BatchPutAttributesType extends SdbMessage {
  String domainName;
  public BatchPutAttributesType() {  }
  ArrayList<ReplaceableItem> item = new ArrayList<ReplaceableItem>();
}
public class SelectResponseType extends SdbMessage {
  public SelectResponseType() {  }
  SelectResult selectResult = new SelectResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class QueryResult extends EucalyptusData {
  String nextToken;
  public QueryResult() {  }
  ArrayList<String> itemName = new ArrayList<String>();
}
public class DomainMetadataType extends SdbMessage {
  String domainName;
  public DomainMetadataType() {  }
}
public class ListDomainsResult extends EucalyptusData {
  String nextToken;
  public ListDomainsResult() {  }
  ArrayList<String> domainName = new ArrayList<String>();
}
public class QueryWithAttributesResponseType extends SdbMessage {
  public QueryWithAttributesResponseType() {  }
  QueryWithAttributesResult queryWithAttributesResult = new QueryWithAttributesResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class ReplaceableAttribute extends EucalyptusData {
  String name;
  String value;
  Boolean replace;
  public ReplaceableAttribute() {  }
}
public class CreateDomainType extends SdbMessage {
  String domainName;
  public CreateDomainType() {  }
}
public class Item extends EucalyptusData {
  String name;
  public Item() {  }
  ArrayList<Attribute> attribute = new ArrayList<Attribute>();
}
public class PutAttributesType extends SdbMessage {
  String domainName;
  String itemName;
  public PutAttributesType() {  }
  ArrayList<ReplaceableAttribute> attribute = new ArrayList<ReplaceableAttribute>();
}
public class GetAttributesResult extends EucalyptusData {
  public GetAttributesResult() {  }
  ArrayList<Attribute> attribute = new ArrayList<Attribute>();
}
public class DeleteDomainType extends SdbMessage {
  String domainName;
  public DeleteDomainType() {  }
}
public class CreateDomainResponseType extends SdbMessage {
  public CreateDomainResponseType() {  }
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class BatchPutAttributesResponseType extends SdbMessage {
  public BatchPutAttributesResponseType() {  }
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
@ComponentMessage(SimpleDB.class)
public class SdbMessage extends BaseMessage {
}
public class ResponseMetadata extends EucalyptusData {
  String requestId;
  String boxUsage;
  public ResponseMetadata() {  }
}
public class Error extends EucalyptusData {
  String type
  String code
  String message
  public Error() {  }
  ErrorDetail detail = new ErrorDetail()
}
public class ErrorDetail extends EucalyptusData {
  public ErrorDetail() {  }
}
public class DeleteAttributesResponseType extends SdbMessage {
  public DeleteAttributesResponseType() {  }
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class DomainMetadataResponseType extends SdbMessage {
  public DomainMetadataResponseType() {  }
  DomainMetadataResult domainMetadataResult = new DomainMetadataResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class PutAttributesResponseType extends SdbMessage {
  public PutAttributesResponseType() {  }
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class QueryWithAttributesType extends SdbMessage {
  String domainName;
  String queryExpression;
  Integer maxNumberOfItems;
  String nextToken;
  public QueryWithAttributesType() {  }
  ArrayList<String> attributeName = new ArrayList<String>();
}
public class SelectType extends SdbMessage {
  String selectExpression;
  String nextToken;
  public SelectType() {  }
}
public class QueryType extends SdbMessage {
  String domainName;
  String queryExpression;
  Integer maxNumberOfItems;
  String nextToken;
  public QueryType() {  }
}
public class QueryResponseType extends SdbMessage {
  public QueryResponseType() {  }
  QueryResult queryResult = new QueryResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class DeleteDomainResponseType extends SdbMessage {
  public DeleteDomainResponseType() {  }
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class QueryWithAttributesResult extends EucalyptusData {
  String nextToken;
  public QueryWithAttributesResult() {  }
  ArrayList<Item> item = new ArrayList<Item>();
}
public class GetAttributesType extends SdbMessage {
  String domainName;
  String itemName;
  public GetAttributesType() {  }
  ArrayList<String> attributeName = new ArrayList<String>();
}
public class DomainMetadataResult extends EucalyptusData {
  String itemCount;
  String itemNamesSizeBytes;
  String attributeNameCount;
  String attributeNamesSizeBytes;
  String attributeValueCount;
  String attributeValuesSizeBytes;
  String timestamp;
  public DomainMetadataResult() {  }
}
public class DeleteAttributesType extends SdbMessage {
  String domainName;
  String itemName;
  public DeleteAttributesType() {  }
  ArrayList<Attribute> attribute = new ArrayList<Attribute>();
}
public class ListDomainsResponseType extends SdbMessage {
  public ListDomainsResponseType() {  }
  ListDomainsResult listDomainsResult = new ListDomainsResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class SelectResult extends EucalyptusData {
  String nextToken;
  public SelectResult() {  }
  ArrayList<Item> item = new ArrayList<Item>();
}
public class Attribute extends EucalyptusData {
  String name;
  String value;
  public Attribute() {  }
}
public class ReplaceableItem extends EucalyptusData {
  String itemName;
  public ReplaceableItem() {  }
  ArrayList<ReplaceableAttribute> attribute = new ArrayList<ReplaceableAttribute>();
}
public class GetAttributesResponseType extends SdbMessage {
  public GetAttributesResponseType() {  }
  GetAttributesResult getAttributesResult = new GetAttributesResult();
  ResponseMetadata responseMetadata = new ResponseMetadata();
}
public class ErrorResponse extends SdbMessage {
  String requestId
  public ErrorResponse() {  }
  ArrayList<Error> error = new ArrayList<Error>()
}
