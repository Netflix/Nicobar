<#macro body>

<#import "/layout/bootstrap/simplepage.ftl" as layout />

<@layout.pagelayout title="Script Archive Repositories">

<!-- Move your JavaScript code to an include file -->
<script type="text/javascript">
<#include "repository_list.js"/>
</script>
 
<!-- Customize your styles here -->
<style>
    div.dataTables_filter label {
        float: left;
        margin-bottom: 15px;
    }
</style>
 

<!-- Define a table -->
<table cellpadding="0" cellspacing="0" border="0" class="table table-striped table-bordered dataTable" id="repository-table" style="width:80%;">
</table>

</@layout.pagelayout>

</#macro>
