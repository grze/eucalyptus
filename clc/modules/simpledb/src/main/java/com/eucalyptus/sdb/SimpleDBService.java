package com.eucalyptus.sdb;

import com.eucalyptus.sdb.BatchPutAttributesResponseType;
import com.eucalyptus.sdb.BatchPutAttributesType;
import com.eucalyptus.sdb.CreateDomainResponseType;
import com.eucalyptus.sdb.CreateDomainType;
import com.eucalyptus.sdb.DeleteAttributesResponseType;
import com.eucalyptus.sdb.DeleteAttributesType;
import com.eucalyptus.sdb.DeleteDomainResponseType;
import com.eucalyptus.sdb.DeleteDomainType;
import com.eucalyptus.sdb.DomainMetadataResponseType;
import com.eucalyptus.sdb.DomainMetadataType;
import com.eucalyptus.sdb.GetAttributesResponseType;
import com.eucalyptus.sdb.GetAttributesType;
import com.eucalyptus.sdb.ListDomainsResponseType;
import com.eucalyptus.sdb.ListDomainsType;
import com.eucalyptus.sdb.PutAttributesResponseType;
import com.eucalyptus.sdb.PutAttributesType;
import com.eucalyptus.sdb.QueryResponseType;
import com.eucalyptus.sdb.QueryType;
import com.eucalyptus.sdb.QueryWithAttributesResponseType;
import com.eucalyptus.sdb.QueryWithAttributesType;
import com.eucalyptus.sdb.SelectResponseType;
import com.eucalyptus.sdb.SelectType;


public class SimpleDBService {
  public CreateDomainResponseType createDomain(CreateDomainType request) {
    CreateDomainResponseType reply = request.getReply( );
    return reply;
  }

  public ListDomainsResponseType listDomains(ListDomainsType request) {
    ListDomainsResponseType reply = request.getReply( );
    return reply;
  }

  public DomainMetadataResponseType domainMetadata(DomainMetadataType request) {
    DomainMetadataResponseType reply = request.getReply( );
    return reply;
  }

  public DeleteDomainResponseType deleteDomain(DeleteDomainType request) {
    DeleteDomainResponseType reply = request.getReply( );
    return reply;
  }

  public PutAttributesResponseType putAttributes(PutAttributesType request) {
    PutAttributesResponseType reply = request.getReply( );
    return reply;
  }

  public BatchPutAttributesResponseType batchPutAttributes(BatchPutAttributesType request) {
    BatchPutAttributesResponseType reply = request.getReply( );
    return reply;
  }

  public GetAttributesResponseType getAttributes(GetAttributesType request) {
    GetAttributesResponseType reply = request.getReply( );
    return reply;
  }

  public DeleteAttributesResponseType deleteAttributes(DeleteAttributesType request) {
    DeleteAttributesResponseType reply = request.getReply( );
    return reply;
  }

  public QueryResponseType query(QueryType request) {
    QueryResponseType reply = request.getReply( );
    return reply;
  }

  public SelectResponseType select(SelectType request) {
    SelectResponseType reply = request.getReply( );
    return reply;
  }

  public QueryWithAttributesResponseType queryWithAttributes(QueryWithAttributesType request) {
    QueryWithAttributesResponseType reply = request.getReply( );
    return reply;
  }

}
