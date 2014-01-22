$(document).ready(function() {
    "use strict";
    document.title = "Script Archive Repository";

    $(".breadcrumb").nfBreadcrumbs({
        crumbs : [
            { text : "All Repositories", href: "."},
            { text : "${repositoryId}" }
        ]
    });

    var oTable = $('#archive-table').dataTable( {
        "aoColumns": [
        	{ "sTitle": "Module ID", "mDataProp" : "moduleId", "sWidth" : "40%", "sDefaultContent": "-" },
        	{ "sTitle": "Metadata", "sWidth" : "30%", "sDefaultContent": "-" ,
        		"fnRender" : function (oObj) {
        			return JSON.stringify(oObj.aData.moduleSpec.archiveMetadata);
        		}
        	},
        	{ "sTitle": "Compilers", "sWidth" : "20%", "sDefaultContent": "-" ,
        		"fnRender" : function (oObj) {
        			return JSON.stringify(oObj.aData.moduleSpec.compilerDependencies);
        		}
        	},
        	{ "sTitle": "Module Dependencies", "sWidth" : "30%", "sDefaultContent": "-" ,
        		"fnRender" : function (oObj) {
        			return JSON.stringify(oObj.aData.moduleSpec.moduleDependencies);
        		}
        	},
          
            { "sTitle": "Last Updated", "sWidth" : "20%", "sDefaultContent": "-",
                "fnRender" : function (oObj) {
                	return new Date(oObj.aData.lastUpdateTime).format();
                }
        	}
        ],
        "sAjaxDataProp": "",
        "sAjaxSource":   "${repositoryId}/archivesummaries",
        "bDestroy"       : true,
        "bAutoWidth"     : true,
        "bStateSave"     : true,
        "bPaginate"      : false,
        "bLengthChange"  : false
    });
});