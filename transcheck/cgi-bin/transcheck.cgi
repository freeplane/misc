#!/usr/bin/perl -wT
# transcheck.cgi - CGI script to compare an uploaded file with a fix reference
# property file.

use strict;
use Encode;
use CGI qw ( escapeHTML, charset );
charset('utf-8');
use CGI::Carp qw ( fatalsToBrowser );
use File::Basename;

# 500kBytes should be enough for a resource translation file
$CGI::POST_MAX = 1024 * 500;
my $safe_filename_characters = "a-zA-Z0-9_.-";
my $reference_dir =
	"/home/groups/f/fr/freeplane/persistent/transcheck/reference";
my $reference_file = "Resources_en.properties";
my $reference_path = $reference_dir . "/" . $reference_file;

my $query = new CGI;
my $filename = $query->param("translation");

sub load_translation_from_file($);
sub load_translation_from_fh($);
sub compare_translations($$);
sub abandon($);

if ( !$filename )
{
 abandon "There was a problem checking your translation (try a smaller file?!).";
}

my ( $name, $path, $extension ) = fileparse ( $filename, '\..*' );
$filename = $name . $extension;
$filename =~ tr/ /_/;
$filename =~ s/[^$safe_filename_characters]//g;

if ( $filename =~ /^([$safe_filename_characters]+)$/ )
{
 $filename = $1;
}
else
{
 abandon "Filename contains invalid characters";
}

my $uploaded_translation = load_translation_from_fh(
				$query->upload("translation") );
my $reference_translation = load_translation_from_file( $reference_path );

my $comparaison = compare_translations($reference_translation,
				$uploaded_translation);

# number of keys in the uploaded translation
my $trans_total = $comparaison->{'trans_total'};
my $ref_total = $comparaison->{'ref_total'};
my $missing_nr = $comparaison->{'missing_nr'};
my $toomuch_nr = $comparaison->{'toomuch_nr'};
my $wrong_nr = $comparaison->{'wrong_nr'};
my $check_nr = $comparaison->{'check_nr'};
my $quality = $comparaison->{'quality'};

print $query->header (-type=>'text/html', -charset => 'utf-8', -expires => '0');
print <<END_HTML;
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
 <head>
   <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
   <meta http-equiv="expires" content="0" />
   <title>Translation check result for $filename</title>
   <link rel="stylesheet" href="/transcheck/transcheck.css" type="text/css" />
 </head>
 <body>
   <h1>Summary</h1>
   <p>If you are satisfied with the result
	(i.e. the quality index is at or near 100%...), you can create an entry
	(called Issue) in
	<a href="http://sourceforge.net/apps/mantisbt/freeplane/search.php?project_id=12&hide_status_id=90">Mantis</a>,
	and attach your translation file to it, copying and pasting the
	following summary into the additional information field:</p>
   <pre>
END_HTML

print "Reference filename : " . $reference_file . "\n";
print "Reference date     : " . localtime((stat $reference_path)[9]) . "\n";
print "Compared filename  : " . $filename . "\n";
print "Comparaison time   : " . localtime() . "\n";
print "Missing keys       : " . $missing_nr . "\n";
print "Superfluous keys   : " . $toomuch_nr . "\n";
print "Wrong keys         : " . $wrong_nr . "\n";
print "Keys needing check : " . $check_nr . "\n";
print "Reference keys     : " . $ref_total . "\n";
print "Quality index      : " . $quality . "%\n";

print <<END_HTML;
   </pre>

   <h1>Missing keys</h1>
   <p>The lines present in the reference but not in your file and that you
   need to add:</p>
   <table border="1">
END_HTML

foreach (keys %{$comparaison->{"missing"}}) {
	print("<tr><td>", escapeHTML($_), "</td>",
		"<td>", escapeHTML($comparaison->{"missing"}->{$_}),
		"</td></tr>\n");
}

print <<END_HTML;
   </table>
   <h1>Superfluous keys</h1>
   <p>The lines present in your file but not in the reference and that you
   need to remove:</p>
   <table border="1">
END_HTML

foreach (keys %{$comparaison->{"toomuch"}}) {
	print("<tr><td>", escapeHTML($_), "</td>",
		"<td>", escapeHTML($comparaison->{"toomuch"}->{$_}),
		"</td></tr>\n");
}

print <<END_HTML;
   </table>
   <h1>Wrong keys</h1>
   <p>The lines present in your file marked with <code>[translate me]</code>,
   i.e. the key exists but hasn't actually been translated, please do it and
   and remove the mark:</p>
   <table border="1">
END_HTML

foreach (keys %{$comparaison->{"wrong"}}) {
	print("<tr><td>", escapeHTML($_), "</td>",
		"<td>", escapeHTML($comparaison->{"wrong"}->{$_}),
		"</td></tr>\n");
}

print <<END_HTML;
   </table>
   <h1>Keys to be checked</h1>
   <p>The lines present in your file marked with <code>[auto]</code>, i.e.
   they have been automatically translated and must be checked,
   and the mark be removed:</p>
   <table border="1">
END_HTML

foreach (keys %{$comparaison->{"check"}}) {
	print("<tr><td>", escapeHTML($_), "</td>",
		"<td>", escapeHTML($comparaison->{"check"}->{$_}),
		"</td></tr>\n");
}

print <<END_HTML;
   </table>
   <h1>File content</h1>
   <p>The content of your file '$filename':</p>
   <table border="1">
END_HTML

foreach (keys %$uploaded_translation) {
	print("<tr><td>", escapeHTML($_), "</td>",
		"<td>", escapeHTML($uploaded_translation->{$_}),
		"</td></tr>\n");
	#print(tr(td(escapeHTML($_)), td(escapeHTML($translation{$_}))), "\n");
}

print <<END_HTML;
  </table>
 </body>
</html>
END_HTML

### FUNCTION DEFINITIONS #######################################################

# load_translation_from_file takes the file name of a resource file
# as parameter, and returns a reference to a hash of keys/translations
# loaded from the given file.
sub load_translation_from_file($) {
	my $file = shift;
	# open the file readonly and use load_translation_from_fh on the file
	# handle.
	open(FH, "< $file") or
		abandon "Couldn't open reference file! Inform the webmaster.";
	my $translation = load_translation_from_fh(*FH); # typeglob of FH
	close(FH);

	return $translation;
}


my %esc = ( "\n" => 'n',
	    "\r" => 'r',
	    "\t" => 't' );
my %unesc = reverse %esc;

sub unescape {
    $_[0]=~s/\\([tnr\\"' =:#!])|\\u([\da-fA-F]{4})/
	defined $1 ? $unesc{$1}||$1 : chr hex $2 /ge;
    $_[0]=encode("utf-8", $_[0]);
}
# load_translation_from_fh takes the file handle (fh) of a resource file
# as parameter, and returns a reference to a hash of keys/translations
# loaded from the given file handle.
sub load_translation_from_fh($) {
	my $filehandle = shift;

	my %translation;
	my $line_continued = 0;
	my $key = "";
	my $value = "";
	my $end_backslashes = "";
	while ( <$filehandle> )
	{
		chomp; s/+$//; # need to get rid of this strange character
		if ($line_continued) {
			if (/^\s*(.*?)(\\+)$/) {
				$value .= $1;
				$end_backslashes = $2;
				if (length($end_backslashes) % 2 != 0) {
					# there is one backslash remaining at
					# the end of the line, which hence
					# continues...
					chop($end_backslashes);
				} else {
					$line_continued = 0;
				}
				$value .= $end_backslashes;
			} elsif (/^\s*(.*)$/) {
				$value .= $1;
				$line_continued = 0;
			} else {
				abandon("Line '$_' impossible to parse.");
			}
		} elsif (/^\s*[#!]/ or /^\s*$/) { # skip empty line or comment
			$line_continued = 0;
			next;
		} elsif (/^\s*(.+?)\s*[\s:=]\s*(.*)(\\+)$/) {
			$key = $1;
			$value = $2;
			$end_backslashes = $3;
			if (length($end_backslashes) % 2 != 0) {
				# there is one backslash remaining at the end
				# of the line, which hence continues...
				chop($end_backslashes);
				$line_continued = 1;
			}
			$value .= $end_backslashes;
		} elsif (/^\s*(.+?)\s*[\s:=]\s*(.*)$/) {
			$key = $1;
			$value = $2;
		}
		if (not $line_continued) {
			unescape $value;
			$translation{$key} = $value;
		}
	}
	if ($line_continued and $key) {
		# the line continued but the file was finished
		unescape $value;
		$translation{$key} = $value;
	}

	return \%translation;
}

sub abandon($) {
 print $query->header ( );
 print shift;
 exit;
}

# compare_translations takes a reference and a translation hash pointer and
# compares them, returning a hash reference to missing and toomuch keys.
sub compare_translations($$) {
	my $ref = shift;
	my $trans = shift;

	my %missing;
	my %toomuch;
	my %wrong;
	my %check;

	local $_;
	foreach (keys %$trans) {
		if (not exists $ref->{$_}) {
			$toomuch{$_} = $trans->{$_};
		} elsif ( $trans->{$_} =~ m/\[auto\]/ ) {
			$check{$_} = $trans->{$_}
				. " (automatically translated from: "
				. $ref->{$_} . ")";
		} elsif ( $trans->{$_} =~ m/\[translate me\]/ ) {
			$wrong{$_} = $trans->{$_}
				. " (Untranslated from: "
				. $ref->{$_} . ")";
		} # TODO - other checks?
	}
	foreach (keys %$ref) {
		if (not exists $trans->{$_}) {
			$missing{$_} = $ref->{$_};
		}
	}

	my $missing_nr = scalar(keys %missing);
	my $toomuch_nr = scalar(keys %toomuch);
	my $wrong_nr = scalar(keys %wrong);
	my $check_nr = scalar(keys %check);
	my $ref_total = scalar(keys %$ref);
	my $trans_total = scalar(keys %$trans);

	# a quality index
	my $quality = int( 100
		* ($trans_total - $toomuch_nr - $check_nr/2 - $wrong_nr)
		/ ($trans_total + $missing_nr) );

	return {
		missing    => \%missing,   toomuch => \%toomuch,
		missing_nr => $missing_nr, toomuch_nr => $toomuch_nr,
		wrong =>    \%wrong,   check => \%check,
		wrong_nr => $wrong_nr, check_nr => $check_nr,
		ref_total => $ref_total, trans_total => $trans_total,
		quality => $quality
	};
}
