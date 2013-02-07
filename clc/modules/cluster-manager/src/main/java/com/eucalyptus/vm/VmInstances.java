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

package com.eucalyptus.vm;

import static com.eucalyptus.cluster.ResourceState.VmTypeAvailability;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Availability;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Dimension;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.ResourceType;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.ResourceType.*;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Tag;
import static com.eucalyptus.reporting.event.ResourceAvailabilityEvent.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityTransaction;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import com.eucalyptus.address.Address;
import com.eucalyptus.address.Addresses;
import com.eucalyptus.blockstorage.State;
import com.eucalyptus.blockstorage.Volumes;
import com.eucalyptus.bootstrap.Bootstrap;
import com.eucalyptus.bootstrap.Hosts;
import com.eucalyptus.cloud.CloudMetadata.VmInstanceMetadata;
import com.eucalyptus.cloud.CloudMetadatas;
import com.eucalyptus.cloud.ImageMetadata;
import com.eucalyptus.cluster.Cluster;
import com.eucalyptus.cluster.Clusters;
import com.eucalyptus.cluster.callback.TerminateCallback;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.component.id.Storage;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableField;
import com.eucalyptus.configurable.ConfigurableProperty;
import com.eucalyptus.configurable.ConfigurablePropertyException;
import com.eucalyptus.configurable.PropertyChangeListener;
import com.eucalyptus.crypto.Crypto;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.event.ClockTick;
import com.eucalyptus.event.EventListener;
import com.eucalyptus.event.ListenerRegistry;
import com.eucalyptus.event.Listeners;
import com.eucalyptus.images.BlockStorageImageInfo;
import com.eucalyptus.images.BootableImageInfo;
import com.eucalyptus.images.ImageInfo;
import com.eucalyptus.network.NetworkGroup;
import com.eucalyptus.network.NetworkGroups;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.records.Logs;
import com.eucalyptus.reporting.event.ResourceAvailabilityEvent;
import com.eucalyptus.tags.FilterSupport;
import com.eucalyptus.util.Callback;
import com.eucalyptus.util.HasNaturalId;
import com.eucalyptus.util.LogUtil;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.RestrictedTypes.QuantityMetricFunction;
import com.eucalyptus.util.RestrictedTypes.Resolver;
import com.eucalyptus.util.Strings;
import com.eucalyptus.util.async.AsyncRequests;
import com.eucalyptus.util.async.Callbacks;
import com.eucalyptus.util.async.DelegatingRemoteCallback;
import com.eucalyptus.util.async.RemoteCallback;
import com.eucalyptus.util.async.Request;
import com.eucalyptus.vm.VmInstance.Transitions;
import com.eucalyptus.vm.VmInstance.VmState;
import com.eucalyptus.vm.VmInstance.VmStateSet;
import com.eucalyptus.vmtypes.VmType;
import com.eucalyptus.vmtypes.VmTypes;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import edu.ucsb.eucalyptus.msgs.DeleteStorageVolumeType;
import edu.ucsb.eucalyptus.msgs.DetachStorageVolumeType;
import edu.ucsb.eucalyptus.msgs.RunningInstancesItemType;

@ConfigurableClass( root = "cloud.vmstate",
                    description = "Parameters controlling the lifecycle of virtual machines." )
public class VmInstances {
  public static class TerminatedInstanceException extends NoSuchElementException {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    TerminatedInstanceException( final String s ) {
      super( s );
    }
    
  }
  
  public enum Timeout implements Predicate<VmInstance> {
    EXPIRED( VmState.RUNNING ) {
      @Override
      public Integer getMinutes( ) {
        return 0;
      }
      
      @Override
      public boolean apply( VmInstance arg0 ) {
        return VmState.RUNNING.apply( arg0 ) && ( System.currentTimeMillis( ) > arg0.getExpiration( ).getTime( ) );
      }
    },
    UNREPORTED( VmState.PENDING, VmState.RUNNING ) {
      @Override
      public Integer getMinutes( ) {
        return INSTANCE_TIMEOUT;
      }
    },
    SHUTTING_DOWN( VmState.SHUTTING_DOWN ) {
      @Override
      public Integer getMinutes( ) {
        return SHUT_DOWN_TIME;
      }
    },
    STOPPING( VmState.STOPPING ) {
      @Override
      public Integer getMinutes( ) {
        return STOPPING_TIME;
      }
    },
    TERMINATED( VmState.TERMINATED ) {
      @Override
      public Integer getMinutes( ) {
        return TERMINATED_TIME;
      }
    };
    private final List<VmState> states;
    
    private Timeout( final VmState... states ) {
      this.states = Arrays.asList( states );
    }
    
    public abstract Integer getMinutes( );
    
    public Integer getSeconds( ) {
      return this.getMinutes( ) * 60;
    }
    
    public Long getMilliseconds( ) {
      return this.getSeconds( ) * 1000l;
    }
    
    @Override
    public boolean apply( final VmInstance arg0 ) {
      return this.inState( arg0.getState( ) ) && ( arg0.getSplitTime( ) > this.getMilliseconds( ) );
    }
    
    protected boolean inState( final VmState state ) {
      return this.states.contains( state );
    }
    
  }
  
  @ConfigurableField( description = "Number of times to retry transactions in the face of potential concurrent update conflicts.",
                      initial = "10" )
  public static final int TX_RETRIES                    = 10;
  @ConfigurableField( description = "Amount of time (in minutes) before a previously running instance which is not reported will be marked as terminated.",
                      initial = "720" )
  public static Integer   INSTANCE_TIMEOUT              = 720;
  @ConfigurableField( description = "Amount of time (in minutes) before a VM which is not reported by a cluster will be marked as terminated.",
                      initial = "10" )
  public static Integer   SHUT_DOWN_TIME                = 10;
  @ConfigurableField( description = "Amount of time (in minutes) before a stopping VM which is not reported by a cluster will be marked as terminated.",
                      initial = "10" )
  public static Integer   STOPPING_TIME                 = 10;
  @ConfigurableField( description = "Amount of time (in minutes) that a terminated VM will continue to be reported.",
                      initial = "60" )
  public static Integer   TERMINATED_TIME               = 60;
  @ConfigurableField( description = "Maximum amount of time (in seconds) that the network topology service takes to propagate state changes.",
                      initial = "" + 60 * 60 * 1000 )
  public static Long      NETWORK_METADATA_REFRESH_TIME = 15l;
  @ConfigurableField( description = "Prefix to use for instance MAC addresses.",
                      initial = "d0:0d" )
  public static String    MAC_PREFIX                    = "d0:0d";
  @ConfigurableField( description = "Subdomain to use for instance DNS.",
                      initial = ".eucalyptus",
                      changeListener = SubdomainListener.class )
  public static String    INSTANCE_SUBDOMAIN            = ".eucalyptus";
  @ConfigurableField( description = "Period (in seconds) between state updates for actively changing state.",
                      initial = "3" )
  public static Long      VOLATILE_STATE_INTERVAL_SEC   = Long.MAX_VALUE;
  @ConfigurableField( description = "Timeout (in seconds) before a requested instance terminate will be repeated.",
                      initial = "60" )
  public static Long      VOLATILE_STATE_TIMEOUT_SEC    = 60l;
  @ConfigurableField( description = "Maximum number of threads the system will use to service blocking state changes.",
                      initial = "16" )
  public static Integer   MAX_STATE_THREADS             = 16;
  @ConfigurableField( description = "Amount of time (in minutes) before a EBS volume backing the instance is created",
                      initial = "30" )
  public static Integer   EBS_VOLUME_CREATION_TIMEOUT   = 30;
  
  public static class SubdomainListener implements PropertyChangeListener {
    @Override
    public void fireChange( final ConfigurableProperty t, final Object newValue ) throws ConfigurablePropertyException {
      
      if ( !newValue.toString( ).startsWith( "." ) || newValue.toString( ).endsWith( "." ) )
        throw new ConfigurablePropertyException( "Subdomain must begin and cannot end with a '.' -- e.g., '." + newValue.toString( ).replaceAll( "\\.$", "" )
                                                 + "' is correct." + t.getFieldName( ) );
      
    }
  }
  
  static ConcurrentMap<String, VmInstance>               terminateCache         = new ConcurrentHashMap<String, VmInstance>( );
  static ConcurrentMap<String, RunningInstancesItemType> terminateDescribeCache = new ConcurrentHashMap<String, RunningInstancesItemType>( );
  
  private static Logger                                  LOG                    = Logger.getLogger( VmInstances.class );
  
  @QuantityMetricFunction( VmInstanceMetadata.class )
  public enum CountVmInstances implements Function<OwnerFullName, Long> {
    INSTANCE;
    
    @Override
    public Long apply( final OwnerFullName input ) {
      final EntityTransaction db = Entities.get( VmInstance.class );
      final long i;
      try {
        i = ((Number) Entities.createCriteria( VmInstance.class )
                    .add( Example.create( VmInstance.named( input, null ) ) )
                    .setReadOnly( true )
                    .setCacheable( false )
                    .setProjection( Projections.rowCount() )
                    .uniqueResult()).longValue();
      } finally {
        db.rollback( );
      }
      return i;
    }
  }
  
  public static String getId( final Long rsvId, final int launchIndex ) {
    String vmId;
    do {
      vmId = Crypto.generateId( Long.toString( rsvId + launchIndex ), "i" );
    } while ( VmInstances.contains( vmId ) );
    return vmId;
  }
  
  public static VmInstance lookupByPrivateIp( final String ip ) throws NoSuchElementException {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      VmInstance vmExample = VmInstance.exampleWithPrivateIp( ip );
      VmInstance vm = ( VmInstance ) Entities.createCriteriaUnique( VmInstance.class )
                                             .add( Example.create( vmExample ).enableLike( MatchMode.EXACT ) )
                                             .add( Restrictions.in( "state", new VmState[] { VmState.RUNNING, VmState.PENDING } ) )
                                             .uniqueResult( );
      if ( vm == null ) {
        throw new NoSuchElementException( "VmInstance with private ip: " + ip );
      }
      db.commit( );
      return vm;
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchElementException( ex.getMessage( ) );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  public static VmVolumeAttachment lookupVolumeAttachment( final String volumeId ) {
    VmVolumeAttachment ret = null;
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      List<VmInstance> vms = Entities.query( VmInstance.create( ) );
      for ( VmInstance vm : vms ) {
        try {
          ret = vm.lookupVolumeAttachment( volumeId );
          if ( ret.getVmInstance( ) == null ) {
            ret.setVmInstance( vm );
          }
        } catch ( NoSuchElementException ex ) {
          continue;
        }
      }
      if ( ret == null ) {
        throw new NoSuchElementException( "VmVolumeAttachment: no volume attachment for " + volumeId );
      }
      db.commit( );
      return ret;
    } catch ( Exception ex ) {
      throw new NoSuchElementException( ex.getMessage( ) );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  public static VmInstance lookupByPublicIp( final String ip ) throws NoSuchElementException {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      VmInstance vmExample = VmInstance.exampleWithPublicIp( ip );
      VmInstance vm = ( VmInstance ) Entities.createCriteriaUnique( VmInstance.class )
                                             .add( Example.create( vmExample ).enableLike( MatchMode.EXACT ) )
                                             .add( Restrictions.in( "state", new VmState[] { VmState.RUNNING, VmState.PENDING } ) )
                                             .uniqueResult( );
      if ( vm == null ) {
        throw new NoSuchElementException( "VmInstance with public ip: " + ip );
      }
      db.commit( );
      return vm;
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      throw new NoSuchElementException( ex.getMessage( ) );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  public static Predicate<VmInstance> withBundleId( final String bundleId ) {
    return new Predicate<VmInstance>( ) {
      @Override
      public boolean apply( final VmInstance vm ) {
        return ( vm.getRuntimeState( ).getBundleTask( ) != null ) && vm.getRuntimeState( ).getBundleTask( ).getBundleId( ).equals( bundleId );
      }
    };
  }
  
  public static VmInstance lookupByBundleId( final String bundleId ) throws NoSuchElementException {
    return Iterables.find( list( ), withBundleId( bundleId ) );
  }

  public static void tryCleanUp( final VmInstance vm ) {
    cleanUp( vm, true );
  }

  public static void cleanUp( final VmInstance vm ) {
    cleanUp( vm, false );
  }

  private static void cleanUp( final VmInstance vm,
                               final boolean rollbackNetworkingOnFailure ) {
    VmState vmLastState = vm.getLastState( );
    VmState vmState = vm.getState( );
    RuntimeException logEx = new RuntimeException( "Cleaning up instance: " + vm.getInstanceId( ) + " " + vmLastState + " -> " + vmState );
    LOG.debug( logEx.getMessage( ) );
    Logs.extreme( ).info( logEx, logEx );
    try {
      if ( NetworkGroups.networkingConfiguration( ).hasNetworking( ) ) {
        try {
          Address address = Addresses.getInstance( ).lookup( vm.getPublicAddress( ) );
          if ( ( address.isAssigned( ) && vm.getInstanceId( ).equals( address.getInstanceId( ) ) ) //assigned to this instance explicitly
               || ( !address.isReallyAssigned( ) && address.isAssigned( ) && VmState.PENDING.equals( vmLastState ) ) ) { //partial assignment implicitly associated with this failed (PENDING->SHUTTINGDOWN) instance
            if ( address.isSystemOwned( ) ) {
              EventRecord.caller( VmInstances.class, EventType.VM_TERMINATING, "SYSTEM_ADDRESS", address.toString( ) ).debug( );
            } else {
              EventRecord.caller( VmInstances.class, EventType.VM_TERMINATING, "USER_ADDRESS", address.toString( ) ).debug( );
            }
            unassignAddress( vm, address, rollbackNetworkingOnFailure );
          }
        } catch ( final NoSuchElementException e ) {
          //PENDING->SHUTTINGDOWN might happen before address info reported in describe instances by CC, need to try finding address
          if ( VmState.PENDING.equals( vmLastState ) ) {
            for ( Address addr : Addresses.getInstance( ).listValues( ) ) {
              if ( addr.getInstanceId( ).equals( vm.getInstanceId( ) ) ) {
                unassignAddress( vm, addr, rollbackNetworkingOnFailure );
                break;
              }
            }
          }
        } catch ( final Exception e1 ) {
          LOG.debug( e1, e1 );
        }
      }
    } catch ( final Exception e ) {
      LOG.error( e );
      Logs.extreme( ).error( e, e );
    }
    try {
      VmInstances.cleanUpAttachedVolumes( vm );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }
    try {
      AsyncRequests.newRequest( new TerminateCallback( vm.getInstanceId( ) ) ).dispatch( vm.getPartition( ) );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }
  }

  private static void unassignAddress( final VmInstance vm,
                                       final Address address,
                                       final boolean rollbackNetworkingOnFailure ) {
    RemoteCallback<?,?> callback = address.unassign().getCallback();
    Callback.Failure failureHander;
    if ( rollbackNetworkingOnFailure ) {
      callback = DelegatingRemoteCallback.suppressException( callback );
      failureHander = new Callback.Failure<java.lang.Object>() {
        @Override
        public void fireException( final Throwable t ) {
          // Revert the cloud state change
          LOG.info( "Unable to assign address " + address.getName() + " for " + vm.getInstanceId() + ", will retry."  );
          if ( address.isPending( ) ) {
            try {
              address.clearPending( );
            } catch ( Exception ex ) {
            }
          }
          try {
            if ( !address.isAllocated() ) {
              address.pendingAssignment();
            }
            address.assign( vm ).clearPending();
          } catch ( Exception e ) {
            LOG.error( e, e );
            LOG.warn( "Address potentially in an inconsistent state: " + LogUtil.dumpObject( address ) );
          }
        }
      };
    } else {
      failureHander = Callbacks.noopFailure();
    }
    AsyncRequests.dispatchSafely( AsyncRequests.newRequest( callback ).then( failureHander ), vm.getPartition() );
  }
  
  private static void cleanUpAttachedVolumes( final VmInstance vm ) {
    try {
      vm.eachVolumeAttachment( new Predicate<VmVolumeAttachment>( ) {
        @Override
        public boolean apply( final VmVolumeAttachment arg0 ) {
          try {
            
            if ( VmStateSet.TERM.apply( vm ) && !"/dev/sda1".equals( arg0.getDevice( ) ) ) {
              try {
                arg0.getVmInstance( ).getTransientVolumeState( ).removeVolumeAttachment( arg0.getVolumeId( ) );
              } catch ( NoSuchElementException ex ) {
                Logs.extreme( ).debug( ex );
              }
            }
            
            try {
              final ServiceConfiguration sc = Topology.lookup( Storage.class, vm.lookupPartition( ) );
              AsyncRequests.sendSync( sc, new DetachStorageVolumeType( arg0.getVolumeId( ) ) );
            } catch ( Exception ex ) {
              LOG.debug( ex );
              Logs.extreme( ).debug( ex, ex );
            }
            
            try {
              //ebs with either default deleteOnTerminate or user specified deleteOnTerminate and TERMINATING instance
              if ( VmStateSet.TERM.apply( vm ) && arg0.getDeleteOnTerminate( ) ) {
                final ServiceConfiguration sc = Topology.lookup( Storage.class, vm.lookupPartition( ) );
                AsyncRequests.dispatch( sc, new DeleteStorageVolumeType( arg0.getVolumeId( ) ) );
                Volumes.lookup( null, arg0.getVolumeId( ) ).setState( State.ANNIHILATING );
              }
            } catch ( Exception ex ) {
              LOG.debug( ex );
              Logs.extreme( ).debug( ex, ex );
            }
            
            return true;
          } catch ( final Exception e ) {
            LOG.error( "Failed to clean up attached volume: "
                       + arg0.getVolumeId( )
                       + " for instance "
                       + vm.getInstanceId( )
                       + ".  The request failed because of: "
                       + e.getMessage( ), e );
            return true;
          }
        }
      } );
    } catch ( final Exception ex ) {
      LOG.error( "Failed to lookup Storage Controller configuration for: " + vm.getInstanceId( ) + " (placement=" + vm.getPartition( ) + ").  " );
    }
  }
  
  public static String asMacAddress( final String instanceId ) {
    return String.format( "%s:%s:%s:%s:%s",
                          VmInstances.MAC_PREFIX,
                          instanceId.substring( 2, 4 ),
                          instanceId.substring( 4, 6 ),
                          instanceId.substring( 6, 8 ),
                          instanceId.substring( 8, 10 ) );
  }
  
  public static VmInstance cachedLookup( final String name ) throws NoSuchElementException, TerminatedInstanceException {
    return CachedLookup.INSTANCE.apply( name );
  }
  
  public static VmInstance lookup( final String name ) throws NoSuchElementException, TerminatedInstanceException {
    return PersistentLookup.INSTANCE.apply( name );
  }

  public static Function<String,VmInstance> lookup() {
    return PersistentLookup.INSTANCE;
  }

  public static VmInstance register( final VmInstance vm ) {
    if ( !terminateDescribeCache.containsKey( vm.getInstanceId( ) ) ) {
      return Transitions.REGISTER.apply( vm );
    } else {
      throw new IllegalArgumentException( "Attempt to register instance which is already terminated." );
    }
  }
  
  public static VmInstance delete( final VmInstance vm ) throws TransactionException {
    try {
      if ( VmStateSet.DONE.apply( vm ) ) {
        delete( vm.getInstanceId( ) );
      }
    } catch ( final Exception ex ) {
      LOG.error( ex, ex );
    }
    return vm;
  }
  
  public static void delete( final String instanceId ) {
    try {
      Entities.asTransaction( VmInstance.class, new Function<String, String>( ) {
        
        @Override
        public String apply( String input ) {
          final EntityTransaction db = Entities.get( VmInstance.class );
          try {
            VmInstance entity = Entities.uniqueResult( VmInstance.named( null, input ) );
            entity.cleanUp( );
            Entities.delete( entity );
            db.commit( );
          } catch ( final Exception ex ) {
            LOG.error( ex );
            Logs.extreme( ).error( ex, ex );
          }finally {
            if ( db.isActive() ) db.rollback();
          }
          return input;
        }
      }, VmInstances.TX_RETRIES ).apply( instanceId );
    } catch ( Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
    }
    terminateDescribeCache.remove( instanceId );
    terminateCache.remove( instanceId );
  }
  
  static void cache( final VmInstance vm ) {
    if ( !terminateDescribeCache.containsKey( vm.getDisplayName( ) ) ) {
      vm.setState( VmState.TERMINATED );
      final RunningInstancesItemType ret = VmInstances.transform( vm );
      terminateDescribeCache.put( vm.getDisplayName( ), ret );
      terminateCache.put( vm.getDisplayName( ), vm );
      Entities.asTransaction( VmInstance.class, Transitions.DELETE, VmInstances.TX_RETRIES ).apply( vm );
    }
  }

  public static void restored( final String instanceId ) {
    terminateDescribeCache.remove( instanceId );
    terminateCache.remove( instanceId );
  }
  
  public static void terminated( final VmInstance vm ) throws TransactionException {
    VmInstances.cache( Entities.asTransaction( VmInstance.class, Transitions.TERMINATED, VmInstances.TX_RETRIES ).apply( vm ) );
  }
  
  public static void terminated( final String key ) throws NoSuchElementException, TransactionException {
    terminated( VmInstance.Lookup.INSTANCE.apply( key ) );
  }
  
  public static void stopped( final VmInstance vm ) throws TransactionException {
    Entities.asTransaction( VmInstance.class, Transitions.STOPPED, VmInstances.TX_RETRIES ).apply( vm );
  }
  
  public static void stopped( final String key ) throws NoSuchElementException, TransactionException {
    VmInstance vm = VmInstance.Lookup.INSTANCE.apply( key );
    if ( vm.getBootRecord( ).getMachine( ) instanceof BlockStorageImageInfo ) {
      VmInstances.stopped( vm );
    }
  }
  
  public static void shutDown( final VmInstance vm ) throws TransactionException {
    if ( !VmStateSet.DONE.apply( vm ) ) {
//      if ( terminateDescribeCache.containsKey( vm.getDisplayName( ) ) ) {
//        VmInstances.delete( vm );
//      } else {
//        VmInstances.terminated( vm );
//      }
//    } else {
      Entities.asTransaction( VmInstance.class, Transitions.SHUTDOWN, VmInstances.TX_RETRIES ).apply( vm );
    }
  }
  
  public static List<VmInstance> list( ) {
    return list( null );
  }
  
  public static List<VmInstance> list( @Nullable Predicate<? super VmInstance> predicate ) {
    return list( null, null, predicate );
  }
  
  public static List<VmInstance> list( @Nullable OwnerFullName ownerFullName,
                                       @Nullable Predicate<? super VmInstance> predicate ) {
    return list( ownerFullName, null, predicate );
  }

  public static List<VmInstance> list( @Nullable final OwnerFullName ownerFullName,
                                       final Criterion criterion,
                                       final Map<String,String> aliases,
                                       @Nullable final Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.named( ownerFullName, null ), false, criterion, aliases );
      }
    }, predicate );
  }

  public static List<VmInstance> list( @Nullable String instanceId,
                                       @Nullable Predicate<? super VmInstance> predicate ) {
    return list( null, instanceId, predicate );
  }
  
  public static List<VmInstance> list( @Nullable final OwnerFullName ownerFullName,
                                       @Nullable final String instanceId,
                                       @Nullable Predicate<? super VmInstance> predicate ) {
    return list( new Supplier<List<VmInstance>>() {
      @Override
      public List<VmInstance> get() {
        return Entities.query( VmInstance.named( ownerFullName, instanceId ) );
      }
    }, predicate );
  }

  private static List<VmInstance> list( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                        @Nullable Predicate<? super VmInstance> predicate ) {
    predicate = checkPredicate( predicate );
    List<VmInstance> ret = listPersistent( instancesSupplier, predicate );
    ret.addAll( Collections2.filter( terminateCache.values( ), predicate ) );
    return ret;
  }

  private static List<VmInstance> listPersistent( @Nonnull Supplier<List<VmInstance>> instancesSupplier,
                                                  @Nonnull Predicate<? super VmInstance> predicate ) {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      final Iterable<VmInstance> vms = Iterables.filter( instancesSupplier.get(), predicate );
      db.commit( );
      return Lists.newArrayList( vms );
    } catch ( final Exception ex ) {
      LOG.error( ex );
      Logs.extreme( ).error( ex, ex );
      return Lists.newArrayList( );
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  private static <T> Predicate<T> checkPredicate( Predicate<T> predicate ) {
    return predicate == null ?
        Predicates.<T>alwaysTrue() :
        predicate;
  }
  
  public static boolean contains( final String name ) {
    final EntityTransaction db = Entities.get( VmInstance.class );
    try {
      final VmInstance vm = Entities.uniqueResult( VmInstance.named( null, name ) );
      db.commit( );
      return true;
    } catch ( final RuntimeException ex ) {
      return false;
    } catch ( final TransactionException ex ) {
      return false;
    } finally {
      if ( db.isActive() ) db.rollback();
    }
  }
  
  /**
   *
   */
  public static RunningInstancesItemType transform( final VmInstance vm ) {
    if ( terminateDescribeCache.containsKey( vm.getDisplayName( ) ) ) {
      return terminateDescribeCache.get( vm.getDisplayName( ) );
    } else {
      return VmInstance.Transform.INSTANCE.apply( vm );
    }
  }

  public static Function<VmInstance,String> toInstanceUuid() {
    return Functions.compose( HasNaturalId.Utils.toNaturalId(), Functions.<VmInstance>identity() );
  }

  enum PersistentLookup implements Function<String, VmInstance> {
    INSTANCE;
    
    /**
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Override
    public VmInstance apply( final String name ) {
      if ( ( name != null ) && VmInstances.terminateDescribeCache.containsKey( name ) ) {
        throw new TerminatedInstanceException( name );
      } else {
        return VmInstance.Lookup.INSTANCE.apply( name );
      }
    }
    
  }
  
  @Resolver( VmInstanceMetadata.class )
  public enum CachedLookup implements Function<String, VmInstance> {
    INSTANCE;
    
    /**
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Override
    public VmInstance apply( final String name ) {
      VmInstance vm = null;
      if ( ( name != null ) ) {
        vm = VmInstances.terminateCache.get( name );
        if ( vm == null ) {
          vm = PersistentLookup.INSTANCE.apply( name );
        }
      }
      return vm;
    }
    
  }
  
  /**
   *
   */
  public static RunningInstancesItemType transform( final String name ) {
    if ( terminateDescribeCache.containsKey( name ) ) {
      return terminateDescribeCache.get( name );
    } else {
      return VmInstance.Transform.INSTANCE.apply( lookup( name ) );
    }
  }

  public static Function<VmInstance,VmBundleTask> bundleTask() {
    return VmInstanceToVmBundleTask.INSTANCE;
  }

  public static class VmInstanceAvailabilityEventListener implements EventListener<ClockTick> {

    private static final class AvailabilityAccumulator {
      private long total;
      private long available;
      private final Function<VmType,Integer> valueExtractor;
      private final List<Availability> availabilities = Lists.newArrayList();

      private AvailabilityAccumulator( final Function<VmType,Integer> valueExtractor ) {
        this.valueExtractor = valueExtractor;
      }

      private void rollUp( final Iterable<Tag> tags ) {
        availabilities.add( new Availability( total, available, tags ) );
        total = 0;
        available = 0;
      }
    }

    public static void register( ) {
      Listeners.register( ClockTick.class, new VmInstanceAvailabilityEventListener() );
    }

    @Override
    public void fireEvent( final ClockTick event ) {
      if ( Bootstrap.isFinished() && Hosts.isCoordinator() ) {

        final List<ResourceAvailabilityEvent> resourceAvailabilityEvents = Lists.newArrayList();
        final Map<ResourceType,AvailabilityAccumulator> availabilities = Maps.newEnumMap(ResourceType.class);
        final Iterable<VmType> vmTypes = Lists.newArrayList(VmTypes.list());
        for ( final Cluster cluster : Clusters.getInstance().listValues() ) {
          availabilities.put( Core, new AvailabilityAccumulator( VmType.SizeProperties.Cpu ) );
          availabilities.put( Disk, new AvailabilityAccumulator( VmType.SizeProperties.Disk ) );
          availabilities.put( Memory, new AvailabilityAccumulator( VmType.SizeProperties.Memory ) );

          for ( final VmType vmType : vmTypes ) {
            final VmTypeAvailability va = cluster.getNodeState().getAvailability( vmType.getName() );

            resourceAvailabilityEvents.add( new ResourceAvailabilityEvent( Instance, new Availability( va.getMax(), va.getAvailable(), Lists.<Tag>newArrayList(
                new Dimension( "availabilityZone", cluster.getPartition() ),
                new Dimension( "cluster", cluster.getName() ),
                new Type( "vm-type", vmType.getName() )
                ) ) ) );

            for ( final AvailabilityAccumulator availability : availabilities.values() ) {
              availability.total = Math.max( availability.total, va.getMax() * availability.valueExtractor.apply(vmType) );
              availability.available = Math.max( availability.available, va.getAvailable() * availability.valueExtractor.apply(vmType) );
            }
          }

          for ( final AvailabilityAccumulator availability : availabilities.values() ) {
            availability.rollUp(  Lists.<Tag>newArrayList(
                new Dimension( "availabilityZone", cluster.getPartition() ),
                new Dimension( "cluster", cluster.getName() )
            ) );
          }
        }

        for ( final Map.Entry<ResourceType,AvailabilityAccumulator> entry : availabilities.entrySet() )  {
          resourceAvailabilityEvents.add( new ResourceAvailabilityEvent( entry.getKey(), entry.getValue().availabilities ) );
        }

        for ( final ResourceAvailabilityEvent resourceAvailabilityEvent : resourceAvailabilityEvents  ) try {
          ListenerRegistry.getInstance().fireEvent( resourceAvailabilityEvent );
        } catch ( Exception ex ) {
          LOG.error( ex, ex );
        }

      }
    }
  }

  private static <T> Set<T> blockDeviceSet( final VmInstance instance,
                                            final Function<? super VmVolumeAttachment,T> transform ) {
    return Sets.newHashSet( Iterables.transform(
        Iterables.concat(
            instance.getBootRecord( ).getPersistentVolumes( ),
            instance.getTransientVolumeState( ).getAttachments( ) ),
        transform ) );
  }

  private static <T> Set<T> networkGroupSet( final VmInstance instance,
                                             final Function<? super NetworkGroup,T> transform ) {
    return instance.getNetworkGroups() != null ?
        Sets.newHashSet( Iterables.transform( instance.getNetworkGroups(), transform ) ) :
        Collections.<T>emptySet();
  }

  public static class VmInstanceFilterSupport extends FilterSupport<VmInstance> {
    public VmInstanceFilterSupport() {
      super( builderFor( VmInstance.class )
          .withTagFiltering( VmInstanceTag.class, "instance" )
          .withStringProperty( "architecture", VmInstanceFilterFunctions.ARCHITECTURE )
          .withStringProperty( "availability-zone", VmInstanceFilterFunctions.AVAILABILITY_ZONE )
          .withDateSetProperty( "block-device-mapping.attach-time", VmInstanceDateSetFilterFunctions.BLOCK_DEVICE_MAPPING_ATTACH_TIME )
          .withBooleanSetProperty( "block-device-mapping.delete-on-termination", VmInstanceBooleanSetFilterFunctions.BLOCK_DEVICE_MAPPING_DELETE_ON_TERMINATE )
          .withStringSetProperty( "block-device-mapping.device-name", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_DEVICE_NAME )
          .withStringSetProperty( "block-device-mapping.status", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_STATUS )
          .withStringSetProperty( "block-device-mapping.volume-id", VmInstanceStringSetFilterFunctions.BLOCK_DEVICE_MAPPING_VOLUME_ID )
          .withUnsupportedProperty( "client-token" )
          .withStringProperty( "dns-name", VmInstanceFilterFunctions.DNS_NAME )
          .withStringSetProperty( "group-id", VmInstanceStringSetFilterFunctions.GROUP_ID )
          .withStringSetProperty( "group-name", VmInstanceStringSetFilterFunctions.GROUP_NAME )
          .withStringProperty( "image-id", VmInstanceFilterFunctions.IMAGE_ID )
          .withStringProperty( "instance-id", CloudMetadatas.toDisplayName() )
          .withConstantProperty( "instance-lifecycle", "" )
          .withIntegerProperty( "instance-state-code", VmInstanceIntegerFilterFunctions.INSTANCE_STATE_CODE )
          .withStringProperty( "instance-state-name", VmInstanceFilterFunctions.INSTANCE_STATE_NAME )
          .withStringProperty( "instance-type", VmInstanceFilterFunctions.INSTANCE_TYPE )
          .withUnsupportedProperty( "instance.group-id" )
          .withUnsupportedProperty( "instance.group-name" )
          .withStringProperty( "ip-address", VmInstanceFilterFunctions.IP_ADDRESS )
          .withStringProperty( "kernel-id", VmInstanceFilterFunctions.KERNEL_ID )
          .withStringProperty( "key-name", VmInstanceFilterFunctions.KEY_NAME )
          .withStringProperty( "launch-index", VmInstanceFilterFunctions.LAUNCH_INDEX )
          .withDateProperty( "launch-time", VmInstanceDateFilterFunctions.LAUNCH_TIME )
          .withUnsupportedProperty( "monitoring-state" )
          .withStringProperty( "owner-id", VmInstanceFilterFunctions.OWNER_ID )
          .withUnsupportedProperty( "placement-group-name" )
          .withStringProperty( "platform", VmInstanceFilterFunctions.PLATFORM )
          .withStringProperty( "private-dns-name", VmInstanceFilterFunctions.PRIVATE_DNS_NAME )
          .withStringProperty( "private-ip-address", VmInstanceFilterFunctions.PRIVATE_IP_ADDRESS )
          .withUnsupportedProperty( "product-code" )
          .withUnsupportedProperty( "product-code.type" )
          .withStringProperty( "ramdisk-id", VmInstanceFilterFunctions.RAMDISK_ID )
          .withStringProperty( "reason", VmInstanceFilterFunctions.REASON )
          .withUnsupportedProperty( "requester-id" )
          .withStringProperty( "reservation-id", VmInstanceFilterFunctions.RESERVATION_ID )
          .withStringProperty( "root-device-name", VmInstanceFilterFunctions.ROOT_DEVICE_NAME )
          .withStringProperty( "root-device-type", VmInstanceFilterFunctions.ROOT_DEVICE_TYPE )
          .withUnsupportedProperty( "source-dest-check" )
          .withUnsupportedProperty( "spot-instance-request-id" )
          .withUnsupportedProperty( "state-reason-code" )
          .withUnsupportedProperty( "state-reason-message" )
          .withUnsupportedProperty( "subnet-id" )
          .withUnsupportedProperty( "virtualization-type" )
          .withUnsupportedProperty( "vpc-id" )
          .withUnsupportedProperty( "hypervisor" )
          .withUnsupportedProperty( "network-interface.description" )
          .withUnsupportedProperty( "network-interface.subnet-id" )
          .withUnsupportedProperty( "network-interface.vpc-id" )
          .withUnsupportedProperty( "network-interface.network-interface.id" )
          .withUnsupportedProperty( "network-interface.owner-id" )
          .withUnsupportedProperty( "network-interface.availability-zone" )
          .withUnsupportedProperty( "network-interface.requester-id" )
          .withUnsupportedProperty( "network-interface.requester-managed" )
          .withUnsupportedProperty( "network-interface.status" )
          .withUnsupportedProperty( "network-interface.mac-address" )
          .withUnsupportedProperty( "network-interface-private-dns-name" )
          .withUnsupportedProperty( "network-interface.source-destination-check" )
          .withUnsupportedProperty( "network-interface.group-id" )
          .withUnsupportedProperty( "network-interface.group-name" )
          .withUnsupportedProperty( "network-interface.attachment.attachment-id" )
          .withUnsupportedProperty( "network-interface.attachment.instance-id" )
          .withUnsupportedProperty( "network-interface.attachment.instance-owner-id" )
          .withUnsupportedProperty( "network-interface.addresses.private-ip-address" )
          .withUnsupportedProperty( "network-interface.attachment.device-index" )
          .withUnsupportedProperty( "network-interface.attachment.status" )
          .withUnsupportedProperty( "network-interface.attachment.attach-time" )
          .withUnsupportedProperty( "network-interface.attachment.delete-on-termination" )
          .withUnsupportedProperty( "network-interface.addresses.primary" )
          .withUnsupportedProperty( "network-interface.addresses.association.public-ip" )
          .withUnsupportedProperty( "network-interface.addresses.association.ip-owner-id" )
          .withUnsupportedProperty( "association.public-ip" )
          .withUnsupportedProperty( "association.ip-owner-id" )
          .withUnsupportedProperty( "association.allocation-id" )
          .withUnsupportedProperty( "association.association-id" )
          .withPersistenceAlias( "bootRecord.machineImage", "image" )
          .withPersistenceAlias( "networkGroups", "networkGroups" )
          .withPersistenceAlias( "bootRecord.vmType", "vmType" )
          .withPersistenceFilter( "architecture", "image.architecture", Sets.newHashSet("bootRecord.machineImage"), Enums.valueOfFunction( ImageMetadata.Architecture.class ) )
          .withPersistenceFilter( "availability-zone", "placement.partitionName", Collections.<String>emptySet() )
          .withPersistenceFilter( "group-id", "networkGroups.groupId" )
          .withPersistenceFilter( "group-name", "networkGroups.displayName" )
          .withPersistenceFilter( "image-id", "image.displayName", Sets.newHashSet("bootRecord.machineImage") )
          .withPersistenceFilter( "instance-id", "displayName" )
          .withPersistenceFilter( "instance-type", "vmType.name", Sets.newHashSet("bootRecord.vmType")  )
          .withPersistenceFilter( "kernel-id", "image.kernelId", Sets.newHashSet("bootRecord.machineImage") )
          .withPersistenceFilter( "launch-index", "launchRecord.launchIndex", Collections.<String>emptySet(), PersistenceFilter.Type.Integer )
          .withPersistenceFilter( "launch-time", "launchRecord.launchTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
          .withPersistenceFilter( "owner-id", "ownerAccountNumber" )
          .withPersistenceFilter( "ramdisk-id", "image.ramdiskId", Sets.newHashSet("bootRecord.machineImage") )
          .withPersistenceFilter( "reservation-id", "vmId.reservationId", Collections.<String>emptySet() )
      );
    }
  }

  public static class VmBundleTaskFilterSupport extends FilterSupport<VmBundleTask> {
    private enum ProgressToInteger implements Function<String,Integer> {
      INSTANCE {
        @Override
        public Integer apply( final String textValue ) {
          String cleanedValue = textValue;
          if ( cleanedValue.endsWith( "%" ) ) {
            cleanedValue = cleanedValue.substring( 0, cleanedValue.length() - 1 );
          }
          try {
            return java.lang.Integer.valueOf( cleanedValue );
          } catch ( NumberFormatException e ) {
            return null;
          }
        }
      }
    }

    public VmBundleTaskFilterSupport() {
      super( builderFor( VmBundleTask.class )
          .withStringProperty( "bundle-id", BundleFilterFunctions.BUNDLE_ID )
          .withStringProperty( "error-code", BundleFilterFunctions.ERROR_CODE )
          .withStringProperty( "error-message", BundleFilterFunctions.ERROR_MESSAGE )
          .withStringProperty( "instance-id", BundleFilterFunctions.INSTANCE_ID )
          .withStringProperty( "progress", BundleFilterFunctions.PROGRESS )
          .withStringProperty( "s3-bucket", BundleFilterFunctions.S3_BUCKET )
          .withStringProperty( "s3-prefix", BundleFilterFunctions.S3_PREFIX )
          .withDateProperty( "start-time", BundleDateFilterFunctions.START_TIME )
          .withStringProperty( "state", BundleFilterFunctions.STATE )
          .withDateProperty( "update-time", BundleDateFilterFunctions.UPDATE_TIME )
          .withPersistenceFilter( "error-code", "runtimeState.bundleTask.errorCode", Collections.<String>emptySet() )
          .withPersistenceFilter( "error-message", "runtimeState.bundleTask.errorMessage", Collections.<String>emptySet() )
          .withPersistenceFilter( "instance-id", "displayName" )
          .withPersistenceFilter( "progress", "runtimeState.bundleTask.progress", Collections.<String>emptySet(), ProgressToInteger.INSTANCE )
          .withPersistenceFilter( "s3-bucket", "runtimeState.bundleTask.bucket", Collections.<String>emptySet() )
          .withPersistenceFilter( "s3-prefix", "runtimeState.bundleTask.prefix", Collections.<String>emptySet() )
          .withPersistenceFilter( "start-time", "runtimeState.bundleTask.startTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
          .withPersistenceFilter( "state", "runtimeState.bundleTask.state", Collections.<String>emptySet(), Enums.valueOfFunction( VmBundleTask.BundleState.class ) )
          .withPersistenceFilter( "update-time", "runtimeState.bundleTask.updateTime", Collections.<String>emptySet(), PersistenceFilter.Type.Date )
      );
    }
  }

  private enum BundleDateFilterFunctions implements Function<VmBundleTask,Date> {
    START_TIME {
      @Override
      public Date apply( final VmBundleTask bundleTask ) {
        return bundleTask.getStartTime();
      }
    },
    UPDATE_TIME {
      @Override
      public Date apply( final VmBundleTask bundleTask ) {
        return bundleTask.getUpdateTime();
      }
    },
  }

  private enum BundleFilterFunctions implements Function<VmBundleTask,String> {
    BUNDLE_ID {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getBundleId();
      }
    },
    ERROR_CODE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getErrorCode();
      }
    },
    ERROR_MESSAGE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getErrorMessage();
      }
    },
    INSTANCE_ID {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getInstanceId();
      }
    },
    PROGRESS {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getProgress() + "%";
      }
    },
    S3_BUCKET {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getBucket();
      }
    },
    S3_PREFIX {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getPrefix();
      }
    },
    STATE {
      @Override
      public String apply( final VmBundleTask bundleTask ) {
        return bundleTask.getState().name();
      }
    },
  }

  private enum VmVolumeAttachmentBooleanFilterFunctions implements Function<VmVolumeAttachment,Boolean> {
    DELETE_ON_TERMINATE {
      @Override
      public Boolean apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getDeleteOnTerminate();
      }
    },
  }

  private enum VmVolumeAttachmentDateFilterFunctions implements Function<VmVolumeAttachment,Date> {
    ATTACH_TIME {
      @Override
      public Date apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getAttachTime();
      }
    },
  }

  private enum VmVolumeAttachmentFilterFunctions implements Function<VmVolumeAttachment,String> {
    DEVICE_NAME {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getDevice();
      }
    },
    STATUS {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getStatus();
      }
    },
    VOLUME_ID {
      @Override
      public String apply( final VmVolumeAttachment volumeAttachment ) {
        return volumeAttachment.getVolumeId();
      }
    },
  }

  private enum VmInstanceStringSetFilterFunctions implements Function<VmInstance,Set<String>> {
    BLOCK_DEVICE_MAPPING_DEVICE_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.DEVICE_NAME );
      }
    },
    BLOCK_DEVICE_MAPPING_STATUS {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.STATUS );
      }
    },
    BLOCK_DEVICE_MAPPING_VOLUME_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentFilterFunctions.VOLUME_ID );
      }
    },
    GROUP_ID {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkGroupSet( instance, NetworkGroups.groupId() );
      }
    },
    GROUP_NAME {
      @Override
      public Set<String> apply( final VmInstance instance ) {
        return networkGroupSet( instance, CloudMetadatas.toDisplayName() );
      }
    },
  }

  private enum VmInstanceBooleanSetFilterFunctions implements Function<VmInstance,Set<Boolean>> {
    BLOCK_DEVICE_MAPPING_DELETE_ON_TERMINATE {
      @Override
      public Set<Boolean> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentBooleanFilterFunctions.DELETE_ON_TERMINATE );
      }
    },
  }

  private enum VmInstanceDateFilterFunctions implements Function<VmInstance,Date> {
    LAUNCH_TIME {
      @Override
      public Date apply( final VmInstance instance ) {
        return instance.getLaunchRecord().getLaunchTime();
      }
    },
  }

  private enum VmInstanceDateSetFilterFunctions implements Function<VmInstance,Set<Date>> {
    BLOCK_DEVICE_MAPPING_ATTACH_TIME {
      @Override
      public Set<Date> apply( final VmInstance instance ) {
        return blockDeviceSet( instance, VmVolumeAttachmentDateFilterFunctions.ATTACH_TIME );
      }
    },
  }

  private enum VmInstanceIntegerFilterFunctions implements Function<VmInstance,Integer> {
    INSTANCE_STATE_CODE {
      @Override
      public Integer apply( final VmInstance instance ) {
        return instance.getDisplayState().getCode();
      }
    },
  }

  private enum VmInstanceFilterFunctions implements Function<VmInstance,String> {
    ARCHITECTURE {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo instanceof ImageInfo ?
            Strings.toString( ( (ImageInfo) imageInfo ).getArchitecture() ):
            null;
      }
    },
    AVAILABILITY_ZONE {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getPartition();
      }
    },
    DNS_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPublicDnsName();
      }
    },
    IMAGE_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getImageId();
      }
    },
    INSTANCE_STATE_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayState( ) == null ? 
            null : 
            instance.getDisplayState( ).getName( );
      }
    },
    INSTANCE_TYPE {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getVmType() == null ?
            null :
            instance.getVmType().getName( );
      }
    },
    IP_ADDRESS {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPublicAddress();
      }
    },
    KERNEL_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getKernelId();
      }
    },
    KEY_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return CloudMetadatas.toDisplayName().apply( instance.getKeyPair() );
      }
    },
    LAUNCH_INDEX {
      @Override
      public String apply( final VmInstance instance ) {
        return Strings.toString( instance.getLaunchRecord().getLaunchIndex() );
      }
    },
    OWNER_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getOwnerAccountNumber();
      }
    },
    PLATFORM {
      @Override
      public String apply( final VmInstance instance ) {
        return "windows".equals( instance.getPlatform() ) ? "windows" : "";
      }
    },
    PRIVATE_DNS_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPrivateDnsName();
      }
    },
    PRIVATE_IP_ADDRESS {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getDisplayPrivateAddress();
      }
    },
    RAMDISK_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getRamdiskId();
      }
    },
    REASON {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getRuntimeState() == null ?
            null :
            instance.getRuntimeState().getReason();
      }
    },
    RESERVATION_ID {
      @Override
      public String apply( final VmInstance instance ) {
        return instance.getReservationId();
      }
    },
    ROOT_DEVICE_NAME {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo == null ? null : imageInfo.getRootDeviceName();
      }
    },
    ROOT_DEVICE_TYPE {
      @Override
      public String apply( final VmInstance instance ) {
        final BootableImageInfo imageInfo = instance.getBootRecord().getMachine();
        return imageInfo == null ? null : imageInfo.getRootDeviceType();
      }
    },
  }

  private enum VmInstanceToVmBundleTask implements Function<VmInstance,VmBundleTask> {
    INSTANCE {
      @Override
      public VmBundleTask apply( final VmInstance vmInstance ) {
        return vmInstance.getRuntimeState() == null ? null : vmInstance.getRuntimeState().getBundleTask();
      }
    }
  }
}
