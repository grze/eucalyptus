/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.auth.login;

import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.net.URLCodec;
import com.eucalyptus.auth.AccessKeys;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.api.BaseLoginModule;
import com.eucalyptus.auth.principal.AccessKey;
import com.eucalyptus.crypto.util.B64;
import com.eucalyptus.crypto.util.SecurityParameter;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

/**
 * Support class for HMAC login modules
 */
abstract class HmacLoginModuleSupport extends BaseLoginModule<HmacCredentials> {

  private final int signatureVersion;
  private final URLCodec codec = new URLCodec();

  protected HmacLoginModuleSupport( final int signatureVersion ) {
    this.signatureVersion = signatureVersion;
  }

  @Override
  public boolean accepts( ) {
    return super.getCallbackHandler( ) instanceof HmacCredentials && ((HmacCredentials)super.getCallbackHandler( )).getSignatureVersion( ).equals( signatureVersion );
  }

  @Override
  public void reset( ) {
  }

  protected AccessKey lookupAccessKey( final HmacCredentials credentials ) throws AuthException {
    return lookupAccessKey( credentials.getQueryId( ), credentials.getParameters() );
  }

  protected AccessKey lookupAccessKey( final String accessKeyId, final Map<String,List<String>> parameters ) throws AuthException {
    final String token = Iterables.getFirst( Objects.firstNonNull( parameters.get( SecurityParameter.SecurityToken.parameter() ), Collections.<String>emptyList()), null);
    final AccessKey key =  AccessKeys.lookupAccessKey( accessKeyId, token );
    if ( !key.isActive() ) throw new AuthException( "Invalid access key or token" );
    return key;
  }
  
  protected void checkForReplay( final String signature ) throws AuthenticationException {
    SecurityContext.enqueueSignature( normalize(signature) );
  }

  protected String urldecode( final String text ) {
    return URLDecoder.decode( text );
  }
  
  protected String urlencode( final String text ) {
    final byte[] textBytes = Strings.nullToEmpty( text ).getBytes( Charsets.UTF_8 );
    return new String( codec.encode( textBytes ), Charsets.US_ASCII ).replace( "+", "%20" );    
  }

  protected String sanitize( final String b64text ) {
    // There should only be trailing =, it is not clear why
    // we replace = at other locations in B64 data
    return b64text.replace( "=", "" );
  }

  protected String normalize( final String signature ) {
    final String urldecoded = urldecode( signature );
    final String decoded = urldecoded.replace( ' ', '+' ); // url decoding could remove valid b64 characters
    final String sanitized = sanitize( decoded );
    final String normalized;
    int lastBlockLength = sanitized.length() % 4;
    if( lastBlockLength > 0 ) {
      normalized =
          sanitized + Strings.repeat( "=", 4 - lastBlockLength );
    } else {
      normalized = sanitized;
    }
    return B64.standard.encString(B64.standard.dec(normalized));
  }
}
