#!/usr/bin/perl -wT
# translist.cgi - CGI script to download translation property files from
# Bazaar and list them with their quality compared with the reference file
# in English.
# Sadly, because bzr doesn't exist in SourceForge's web environment, this
# script needs to be called from time to time from the shell environment.

use strict;
use Encode;
use CGI qw ( escapeHTML charset );
charset('utf-8');
use CGI::Carp qw ( fatalsToBrowser );
use File::Basename;
use File::Path;

# Change the following line to point to Config.pm and adapt Config.pm
push @INC, "/home/ericl/Public/FREEPLANE/misc/transcheck/transcheck.files";
require "Config.pmc";

# Functions
sub debug($);
sub export($$);
sub list_directory($$$);
sub list_check_directory($$$);
sub load_translation_from_file($);
sub load_translation_from_fh($);
sub compare_translations($$);
sub create_analysis_file($$$);

#### MAIN ####

my $query = new CGI;

# for security reasons, we empty the $PATH environment variable
$ENV{'PATH'} = '';

if (not -d $Config::TARGET_DIR) {
	die("Target directory '$Config::TARGET_DIR' doesn't exist.");
}

export($Config::EXPORT_TRANS_PATH,$Config::TARGET_TRANS_PATH);
export($Config::EXPORT_REF_PATH,$Config::TARGET_REF_PATH);

print $query->header(-type=>'text/html', -charset => 'utf-8',-expires=>'900s');

print <<END_HTML;
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
 <head>
   <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
   <title>Current state of Freeplane translations in Bazaar</title>
   <link rel="stylesheet" href="/transcheck/transcheck.css" type="text/css" />
 </head>
 <body>
END_HTML

list_check_directory($Config::TARGET_TRANS_PATH, "Translations", "trans");
list_directory($Config::TARGET_REF_PATH, "Reference", "ref");

print <<END_HTML;
 </body>
</html>
END_HTML

#### FUNCTIONS ####

sub list_check_directory($$$) {
	my $dir = shift;
	my $name = shift;
	my $type = shift;

	my $ref_hash = load_translation_from_file($Config::REFERENCE_PATH);

	opendir(DIR, $dir) or
		die "Can't open $name directory: $!";
	my $file;
	print "<h1><a name=\"$name\">$name</a></h1>\n";
	print "<table border=\"1\">\n";

	my $date = localtime((stat $dir)[9]);
	print <<END_HTML;
	<p>The below table shows the quality of each translation file as found
	in the Bazaar code repository of
	<a href="http://freeplane.org/">Freeplane</a> under
	<a href="${Config::HTML_LINK}">${Config::EXPORT_ROOT}</a>,
	as of <strong>$date</strong>.</p>
	<p>You are very much invited to download the files of the languages
	you speak well and which have not reached 100% of quality, and improve
	them using the detailed analysis reachable under the "quality link".</p>
	<p>Once you're done with your changes, you should check the resulting
	file with <a href="/transcheck">TransCheck</a> and follow further
	instructions.</p>
	<p>The <a href="/mediawiki-1.14.1/index.php/Translation_How-To">Translation How-To</a> might contain some additional hints.</p>
END_HTML

	print "<tr><th>File</th><th>Language</th><th>Keys</th><th>Missing</th><th>Superfluous</th><th>Wrong</th><th>Check</th><th>Quality</th></tr>\n";
	while ( defined($file = readdir DIR) ) {
		next if ($file !~ m/^Resources_\w+\.properties$/i);
		my $trans_hash = load_translation_from_file($dir . '/' . $file);
		my $comparaison = compare_translations($ref_hash, $trans_hash);

		# Use the English keys to detect the language
		my $lang = "???";
		if ($file =~ m/Resources_(.*)\.properties/) {
			$lang = ( $ref_hash->{'OptionPanel.' . $1} or $1 );
		}

		# number of keys in the given translation
		my $trans_total = $comparaison->{'trans_total'};
		my $ref_total = $comparaison->{'ref_total'};
		my $missing_nr = $comparaison->{'missing_nr'};
		my $toomuch_nr = $comparaison->{'toomuch_nr'};
		my $wrong_nr = $comparaison->{'wrong_nr'};
		my $check_nr = $comparaison->{'check_nr'};
		my $quality = $comparaison->{'quality'};
		my $html_file = create_analysis_file(
					$file, $trans_hash, $comparaison);

		print "<tr style=\"background-color:rgb(" . (100 - $quality)
			. "%," . $quality . "%,0%)\">\n";
		print "  <td><a href=\"/cgi-bin/transgetfile.cgi?how=download&type=$type&file=$file\">$file</a></td>\n  ";
		print "<td>" . $lang        . "</td>";
		print "<td>" . $trans_total . "</td>";
		print "<td>" . $missing_nr  . "</td>";
		print "<td>" . $toomuch_nr  . "</td>";
		print "<td>" . $wrong_nr    . "</td>";
		print "<td>" . $check_nr    . "</td>";
		print "<td><a href=\"/cgi-bin/transgetfile.cgi?how=html&type=html&file=$html_file\">$quality%</a></td>\n";
		print "</tr>\n";
	}
	print "</table>\n";
	closedir(DIR) or
		die "Can't close $name directory: $!";
}

sub list_directory($$$) {
	my $dir = shift;
	my $name = shift;
	my $type = shift;

	opendir(DIR, $dir) or
		die "Can't open $name directory: $!";
	my $file;
	print "<h1><a name=\"$name\">$name</a></h1>\n";
	print "<pre>\n";
	while ( defined($file = readdir DIR) ) {
		print "<a href=\"/cgi-bin/transgetfile.cgi?how=download&type=$type&file=$file\">$file</a> (Version from "
		. localtime((stat $dir . '/' . $file)[9]) . ")\n"
			if ($file =~ m/^Resources_\w+\.properties$/i);
	}
	print "</pre>\n";
	closedir(DIR) or
		die "Can't close $name directory: $!";
}

sub debug($) {
 print STDERR (shift , "\n");
}

# export($$) exports from a source directory into a target directory
sub export($$) {
	my $source = shift;
	my $target = shift;

	if ( (not -d $target) or ( (-M $target) > 1 ) ) {
		my $old_target = $target . ".OLD";
		rmtree($old_target);
		debug "rmtree($old_target)";
		my $rc = system { $Config::EXPORT_COMMAND }
				($Config::EXPORT_COMMAND, $Config::EXPORT_PARAM,
					$old_target, $source);
		if ($rc) {
		# TODO: we know that bzr doesn't exist in web environment
		# of SourceForge hence it will always fail...
		#	die("Export failed from "
		#		. "'$source' to '$old_target' [$rc].");
		} else {
			rmtree($target);
			debug "rmtree($target)";
			rename($old_target,$target);
			debug "rename($old_target,$target)";
		}
	}
}

# load_translation_from_file takes the file name of a resource file
# as parameter, and returns a reference to a hash of keys/translations
# loaded from the given file.
sub load_translation_from_file($) {
	my $file = shift;
	# open the file readonly and use load_translation_from_fh on the file
	# handle.
	open(FH, "< $file") or
		die "Couldn't open file '$file'! Inform the webmaster.";
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
				die("Line '$_' impossible to parse.");
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

sub create_analysis_file($$$) {
	my $filename = shift;
	my $translation = shift;
	my $comparaison = shift;

	die "Unacceptable filename '$filename'."
		if ( $filename !~ m/^(Resources_\w+\.properties)$/i );

	my $html_file = $1 . ".html";
	my $html_path = $Config::TARGET_HTML_PATH . '/' . $html_file;

	# if the file is there and not too old, we return
	if ( (-f $html_path) and
			((-M $html_path) <= (-M $Config::REFERENCE_PATH)) ) {
		return $html_file;
	}

	if ( ! -d $Config::TARGET_HTML_PATH) {
		mkdir($Config::TARGET_HTML_PATH)
			or die("Can't create target HTML directory: $!");
	}

	open(AF, ">", $html_path)
		or die "Couldn't open HTML analysis file '${html_file}': $!";

	# number of keys in the given translation
	my $trans_total = $comparaison->{'trans_total'};
	my $ref_total = $comparaison->{'ref_total'};
	my $missing_nr = $comparaison->{'missing_nr'};
	my $toomuch_nr = $comparaison->{'toomuch_nr'};
	my $wrong_nr = $comparaison->{'wrong_nr'};
	my $check_nr = $comparaison->{'check_nr'};
	my $quality = $comparaison->{'quality'};

	print AF <<END_HTML;
	<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "DTD/xhtml1-strict.dtd">
	<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
	 <head>
	   <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
	   <meta http-equiv="expires" content="900" />
	   <title>Translation check result for $filename</title>
	   <link rel="stylesheet" href="/transcheck/transcheck.css" type="text/css" />
	 </head>
	 <body>
	   <h1>Summary</h1>
	   <p>If you are not satisfied with the quality reached by this
	   language, you are really very welcome to help as explained
	   in our wiki's
	   <a href="/mediawiki-1.14.1/index.php/Translation_How-To">Translation
	   How-To</a>.</p>
	   <pre>
END_HTML

	print AF "Reference filename : " . $Config::REFERENCE_FILE . "\n";
	print AF "Reference date     : "
			. localtime((stat $Config::REFERENCE_PATH)[9]) . "\n";
	print AF "Compared filename  : " . $filename . "\n";
	print AF "Comparaison time   : " . localtime() . "\n";
	print AF "Missing keys       : " . $missing_nr . "\n";
	print AF "Superfluous keys   : " . $toomuch_nr . "\n";
	print AF "Wrong keys         : " . $wrong_nr . "\n";
	print AF "Keys needing check : " . $check_nr . "\n";
	print AF "Reference keys     : " . $ref_total . "\n";
	print AF "Quality index      : " . $quality . "%\n";

	print AF <<END_HTML;
	   </pre>

	   <h1>Missing keys</h1>
	   <p>The lines present in the reference but not in your file and that you
	   need to add:</p>
	   <table border="1">
END_HTML

	foreach (keys %{$comparaison->{"missing"}}) {
		print AF ("<tr><td>", escapeHTML($_), "</td>",
			"<td>", escapeHTML($comparaison->{"missing"}->{$_}),
			"</td></tr>\n");
	}

	print AF <<END_HTML;
	   </table>
	   <h1>Superfluous keys</h1>
	   <p>The lines present in your file but not in the reference and that you
	   need to remove:</p>
	   <table border="1">
END_HTML

	foreach (keys %{$comparaison->{"toomuch"}}) {
		print AF ("<tr><td>", escapeHTML($_), "</td>",
			"<td>", escapeHTML($comparaison->{"toomuch"}->{$_}),
			"</td></tr>\n");
	}

	print AF <<END_HTML;
	   </table>
	   <h1>Wrong keys</h1>
	   <p>The lines present in your file marked with <code>[translate me]</code>,
	   i.e. the key exists but hasn't actually been translated, please do it and
	   and remove the mark:</p>
	   <table border="1">
END_HTML

	foreach (keys %{$comparaison->{"wrong"}}) {
		print AF ("<tr><td>", escapeHTML($_), "</td>",
			"<td>", escapeHTML($comparaison->{"wrong"}->{$_}),
			"</td></tr>\n");
	}

	print AF <<END_HTML;
	   </table>
	   <h1>Keys to be checked</h1>
	   <p>The lines present in your file marked with <code>[auto]</code>, i.e.
	   they have been automatically translated and must be checked,
	   and the mark be removed:</p>
	   <table border="1">
END_HTML

	foreach (keys %{$comparaison->{"check"}}) {
		print AF ("<tr><td>", escapeHTML($_), "</td>",
			"<td>", escapeHTML($comparaison->{"check"}->{$_}),
			"</td></tr>\n");
	}

	print AF <<END_HTML;
	   </table>
	   <h1>File content</h1>
	   <p>The content of your file '$filename':</p>
	   <table border="1">
END_HTML

	foreach (keys %$translation) {
		print AF ("<tr><td>", escapeHTML($_), "</td>",
			"<td>", escapeHTML($translation->{$_}),
			"</td></tr>\n");
		#print AF (tr(td(escapeHTML($_)), td(escapeHTML($translation{$_}))), "\n");
	}

	print AF <<END_HTML;
	  </table>
	 </body>
	</html>
END_HTML

	return $html_file;
}
