<?php

$toBeIgnored = array();
$wanted = array();
include './settings.php';
$pathToFiles = "/home/groups/f/fr/freeplane/persistent/bugreport/";

$log =  $_POST["log"];

foreach($toBeIgnored as $l)
{
    if(false !== strpos($log, $l))
    {
	echo "external bug, report ignored";
	return;
    }
}

$lines = split("\n", $log);
$hashInput = "";
foreach ($lines as $l) {
    if(strpos($l, "\tat org.freeplane.") === 0 || strpos($l, "missing key ") === 0 ){
    	$hashInput = $hashInput . $l;
    }
}
$ver = $_POST["version"];
$rev = $_POST["revision"];
$hashInput = $hashInput . $ver . $rev;

$actualHash = md5($hashInput);
$givenHash =  $_POST["hash"];
if($actualHash != $givenHash)
{
	echo "wrong hash";
# echo $hashInput;
# echo $actualHash;
# echo $givenHash;	
	return;
}

$pathToVersion =  $pathToFiles . $ver;
if(! file_exists($pathToVersion))
{
	echo "unknown freeplane version, report ignored";
	return;
#	mkdir($pathToVersion);
#    chmod($pathToVersion, 0777);
}
$dir = $pathToVersion . "/" . substr($actualHash, 0, 1);
if(! file_exists($dir))
{
	mkdir($dir);
    chmod($dir, 0777);
}
$file = $dir . "/" . $actualHash . ".log";

$newFile = !file_exists($file);

$fh = fopen($file, 'a');
if(! $fh)
{
	echo "can not open file for writing";
	return;
}

chmod($file, 0666);
fwrite($fh, date("F j, Y, G:i"));
if($newFile)
{
	fwrite($fh, "\n");
	fwrite($fh, $log);
}
else
{
	fwrite($fh, " -- " . $rev);
}
fwrite($fh, "\n");
fclose($fh);

foreach($wanted as $l)
{
    if(false !== strpos($log, $l))
    {
	echo "wanted";
	return;
    }
}

echo "ok";

?>

