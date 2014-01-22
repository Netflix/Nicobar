$(document).ready(function() {
    "use strict";
    document.title = "Script Archive Repository Explorer";

    $(".breadcrumb").nfBreadcrumbs({
        crumbs : [
            { text : "All Repositories" }
        ]
    });

    var oTable = $('#repository-table').dataTable( {
        "aoColumns": [
            { "sTitle": "Repository ID", "sWidth" : "30%",  "sDefaultContent": "-",
                "fnRender" : function (oObj) {
                    return "<a href='{0}' class='archive-repistory-view'>{0}</a>".format(oObj.aData.repositoryId);
                }
        	},
            { "sTitle": "Description", "mDataProp" : "description", "sWidth" : "40%", "sDefaultContent": "-" },
            { "sTitle": "Archive Count", "mDataProp" : "archiveCount", "sWidth" : "10%" },
            { "sTitle": "Last Updated", "mDataProp" : "lastUpdated", "sWidth" : "20%",
                "fnRender" : function (oObj) {
                	return new Date(oObj.aData.lastUpdated).format();
                }
        	}
        ],
        "sAjaxDataProp": "",
        "sAjaxSource":   "repositorysummaries",
        "bDestroy"       : true,
        "bAutoWidth"     : false,
        "bStateSave"     : true,
        "bPaginate"      : false,
        "bLengthChange"  : false
    });
});