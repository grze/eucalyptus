#!/usr/bin/perl

delete @ENV{qw(IFS CDPATH ENV BASH_ENV)};
$ENV{'PATH'}='/bin:/usr/bin:/sbin:/usr/sbin/';

$DELIMITER = ",";
$ISCSIADM = untaint(`which iscsiadm`);

# check binaries
if (!-x $ISCSIADM) {
    print STDERR "Unable to find iscsiadm\n";
    do_exit(1);
}

# check input params
$dev_string = untaint(shift @ARGV);

($euca_home, $ip, $store, $passwd, $lun, $auth_mode) = parse_devstring($dev_string);

if (length($euca_home) <= 0) {
    print STDERR "EUCALYPTUS path is not defined.\n";
    do_exit(1);
}
if ((length($lun) > 0) && ($lun > -1)) {
  # get dev from lun
  print get_device_name_from_lun($store, $lun);
} else {
  print get_device_name($store);
}

sub parse_devstring {
    my ($dev_string) = @_;
    return split($DELIMITER, $dev_string);
}

sub get_device_name {
    my ($store) = @_;
    $num_retries = 5;

    for ($i = 0; $i < $num_retries; ++$i) {
      if(!open GETSESSION, "iscsiadm -m session -P 3 |") {
	  print STDERR "Could not get iscsi session information";
	  do_exit(1)
      }
    
      $found_target = 0;
      $attach_seen = 1;    
      while (<GETSESSION>) {
          if($_ =~ /Target: (.*)\n/) {
	      last if $attach_seen == 0; 
	      $found_target = 1 if $1 eq $store;
	      $attach_seen = 0;
	  } elsif($_ =~ /.*Attached scsi disk ([a-zA-Z0-9]+).*\n/) {
	      if($found_target == 1) {
		return "/dev/", $1;
	      }
	      $attach_seen = 1;
	  }
      } 
      close GETSESSION; 
    }
}

sub get_device_name_from_lun {
    my ($store, $lun) = @_;
    $num_retries = 5;

    for ($i = 0; $i < $num_retries; ++$i) {
      if(!open GETSESSION, "iscsiadm -m session -P 3 |") {
          print STDERR "Could not get iscsi session information";
          do_exit(1)
      }

      $found_target = 0;
      $found_lun = 0;
      $attach_seen = 1;
      while (<GETSESSION>) {
          if ($_ =~ /Target: (.*)\n/) {
              last if $attach_seen == 0;
              $found_target = 1 if $1 eq $store;
              $attach_seen = 0;
              $found_lun = 0;
          } elsif ($_ =~ /.*Attached scsi disk ([a-zA-Z0-9]+).*\n/) {
              if ($found_target == 1 && $found_lun == 1) {
                return "/dev/", $1;
              }
              $attach_seen = 1;
          } elsif ($_ =~ /.*Lun: (.*)\n/) {
              $found_lun = 1 if $1 eq $lun;
          }
      }
      close GETSESSION;
    }
}

sub do_exit() {
    $e = shift;

    if ($mounted && ($tmpfile ne "")) {
	system("$mounter umount $tmpfile");
    }
    if ($attached && ($loopdev ne "")) {
	system("$LOSETUP -d $loopdev");
    }
    if ($tmpfile ne "") {
	system("$RMDIR $tmpfile");
    }
    exit($e);
}

sub untaint() {
    $str = shift;
    if ($str =~ /^([ &:#-\@\w.]+)$/) {
	$str = $1; #data is now untainted
    } else {
	$str = "";
    }
    return($str);
}
