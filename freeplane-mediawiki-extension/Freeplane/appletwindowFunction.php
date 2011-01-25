<?php
function getMindMapAppletOutput($mm_title, $parameters, $mm_height, $path) {
$output = '';
if($mm_title != ""){
	$output = '
<p style="text-align:center"><a href="'.$parameters['browsemode_initial_map'].'">'.$mm_title.'</a></p>';
}
$output .= '
<APPLET CODE="org.freeplane.main.applet.FreeplaneApplet.class" ARCHIVE="'.$path.'freeplaneviewer.jar" WIDTH="100%" HEIGHT="'.$mm_height.'">';
	foreach ($parameters as $key => $value)
		$output .="<PARAM NAME=\"$key\" VALUE=\"$value\">\n";
    if(! isset($parameters['selection_method']))
      	$output .="<PARAM NAME=\"selection_method\" VALUE=\"delayed\">\n";
        	$output .= '<PARAM NAME="type"  VALUE="application/x-java-applet;version=1.5">
<PARAM NAME="scriptable"  VALUE="false">
</APPLET>';
return $output;
}
?>
