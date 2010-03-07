<?php

$pathToFiles = "/home/groups/f/fr/freeplane/persistent/bugreport/";

$log =  $_POST["log"];
$lines = split("\n", $log);
$ver = $_POST["version"];
$hashInput = $ver;
foreach ($lines as $l) {
    if(strpos($l, "org.freeplane.")){
    	$hashInput = $hashInput . $l;
    }
}

$actualHash = md5($hashInput);
$givenHash =  $_POST["hash"];
if($actualHash != $givenHash)
{
	echo "wrong hash";
	return;
}

$pathToVersion =  $pathToFiles . $ver;
if(! file_exists($pathToVersion))
{
	echo "old version, report ignored";
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
fwrite($fh, "\n");
if($newFile)
{
	fwrite($fh, $log);
	fwrite($fh, "\n");
}
fclose($fh);
echo "ok";

?>

