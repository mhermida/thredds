
//$(document).ready( function(){
//});

Ncss.initGridAsPoint = function(){
	
	//Layer that will display the selected point
	var point = new OpenLayers.Layer.Vector("point" , {
		styleMap: new OpenLayers.StyleMap({
			pointRadius:3,
			strokeWidth:1,
			strokeColor:"#000000",
			strokeDashstyle:'solid',
			fillOpacity: 0.3,
			fillColor:"#0088B5"
		})
	});	
	
	point.events.register("click", point, function(e){
		
		//TODO: -check if the point is in the polygon
		
			
				
		var latlon = this.map.getLonLatFromPixel(new OpenLayers.Pixel(e.layerX, e.layerY));
		//var latlon = this.map.getLonLatFromPixel(e.xy);

	    var ftr = new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(latlon.lon , latlon.lat));		    
	    point.removeFeatures(point.features);
	    point.addFeatures([ftr]);
	    point.refresh();
		$('#latitude').val(latlon.lat.toFixed(4));
		$('#longitude').val(latlon.lon.toFixed(4));
	    Ncss.log("User clicked on: "+latlon.lat +", "+latlon.lon);
		
	});	
	
	Ncss.initGridAsPointForm();
	Ncss.initMapPreview([point]);

};

Ncss.initGridAsPointForm = function(){
	Ncss.log("initGridAsPointForm...(starts)");
	
	//Add events to temporal subset selectors
	$('#inputTimeRange').click(Ncss.changeTemporalSubsetting);
	$('#inputSingleTime').click(Ncss.changeTemporalSubsetting);
	
	Ncss.fullTimeExt ={
			time_start : $('input[name=dis_time_start]').val(),
			time_end   : $('input[name=dis_time_end]').val()
	};
	
	$('#resetTimeRange').click(function(){
		$('input[name=time_start]').val(Ncss.fullTimeExt.time_start);
		$('input[name=time_end]').val(Ncss.fullTimeExt.time_end);
	});
		
	Ncss.log("initGridAsPointForm...(ends)");

};
