<?php
function getMindMapAppletOutput($mm_title, $parameters, $mm_height, $path) {
$output = '';
if($mm_title != ""){
	$output = '
<p style="text-align:center"><a href="'.$parameters['browsemode_initial_map'].'">'.$mm_title.'</a></p>';
}
$output .= '<applet code="org.freeplane.main.applet.FreeplaneApplet.class" archive="'.$path.'freeplaneviewer.jar" jnlp_href="'.$path.'freeplane_applet.jnlp" width="100%" height="'.$mm_height.'">\n';
$output .= '<script>document.write(\'<param name="location_href" value="\' + window.location.href +\'"/>\');</script>\n';
	foreach ($parameters as $key => $value)
		$output .="<PARAM NAME=\"$key\" VALUE=\"$value\">\n";
    if(! isset($parameters['selection_method']))
      	$output .="<PARAM NAME=\"selection_method\" VALUE=\"delayed\">\n";
        	$output .= '<PARAM NAME="type"  VALUE="application/x-java-applet;version=1.5">
<param name="scriptable" value="false">
</applet>';
return $output;
}
?>
