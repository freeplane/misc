#!/usr/bin/perl -wT
# transgetfile.cgi - CGI script to download a file present in the persistent
# section, or to show it.

use strict;
use CGI qw ( escapeHTML, charset );
charset('utf-8');
use CGI::Carp qw ( fatalsToBrowser );
use File::Basename;
use File::Path;

# Constants to adapt based on your web server configuration
my $TARGET_DIR = "/home/groups/f/fr/freeplane/persistent/transcheck";
my $TARGET_TRANS_DIR = "translations";
my $TARGET_REF_DIR = "reference";
my $TARGET_HTML_DIR = "html";
my $TARGET_TEXT_DIR = "text";

# Let's build a few derived constants...
my $target_trans_path = $TARGET_DIR . "/" . $TARGET_TRANS_DIR;
my $target_ref_path = $TARGET_DIR . "/" . $TARGET_REF_DIR;
my $target_html_path = $TARGET_DIR . "/" . $TARGET_HTML_DIR;
my $target_text_path = $TARGET_DIR . "/" . $TARGET_TEXT_DIR;

sub abandon($);
sub debug($);
sub send_file_to_download($$);

#### MAIN ####

my $query = new CGI;

# for security reasons, we empty the $PATH environment variable
$ENV{'PATH'} = '';

if (not -d $TARGET_DIR) {
	abandon("Target directory for export doesn't exist.");
}

# Gather the parameters from the query:
my $type = $query->param("type");
my $file = $query->param("file");
my $how  = $query->param("how");

# Determine if we can get the right directory
my $dir;
if ($type eq "trans" and -d $target_trans_path) {
	$dir = $target_trans_path;
} elsif ($type eq "ref" and -d $target_ref_path) {
	$dir = $target_ref_path;
} elsif ($type eq "html" and -d $target_html_path) {
	$dir = $target_html_path;
} elsif ($type eq "text" and -d $target_text_path) {
	$dir = $target_text_path;
} else {
	die "Type '$type' isn't one of 'trans', 'ref' or 'html' or the corresponding directory doesn't exist.";
}

opendir(DIR, $dir) or
	die "Can't open required directory: $!";
my $realfile;
while ( defined($realfile = readdir DIR) ) {
	if ( ( $realfile =~ m/^Resources_\w+\.properties$/i
		or ($type eq 'html' and $how eq 'html')
		or ($type eq 'text' and $how eq 'text') )
			and $realfile eq $file ) {
		send_file($dir, $realfile, $how);
	}
}
closedir(DIR);

#### FUNCTIONS ####

# A function to offer any file. The user of this function is responsible to
# make sure that it's OK to send the file to the web user.
sub send_file($$) {
	my $dir = shift;
	my $file = shift;
	my $how = shift;

	my $path = $dir . '/' . $file;

	open(DLFILE, "<$path") or die "File '$file' couldn't be opened: $!";
	my @fileholder = <DLFILE>;
	close (DLFILE) or die "File '$file' couldn't be closed: $!";

	if ($how eq 'download') {
		print "Content-Type:application/x-download\n";
		print "Content-Disposition:attachment;filename=$file\n\n";
	} elsif ($how eq 'text') {
		print $query->header('text/plain',-expires=>'900s');
	} elsif ($how eq 'html') {
		print $query->header(-type=>'text/html', -charset => 'utf-8',-expires=>'900s');
	} else {
		die "Method '$how' isn't one of 'download', 'text' or 'html'.";
	}

	print @fileholder;
	exit;
}

sub abandon($) {
 print $query->header (-expires=>'900s');
 print shift;
 exit;
}

sub debug($) {
 print STDERR (shift , "\n");
}
