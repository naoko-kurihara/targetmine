#!/usr/bin/perl -w

use strict;
use warnings;

use InterMine::PathQuery;
use InterMine::WebService::Service::QueryService;
use InterMine::WebService::Service::ModelService;

my @service_args = ('http://www.flymine.org/query/service', 'service_example');

my $query_service = new InterMine::WebService::Service::QueryService(@service_args);
my $model_service = new InterMine::WebService::Service::ModelService(@service_args);

my $path_query = new InterMine::PathQuery($model_service->get_model());

$path_query->add_view('Organism.name Organism.taxonId');
$path_query->sort_order('Organism.name');

warn 'xml: ', $path_query->to_xml_string(), "\n";

## get the number of result rows
my $count = $query_service->get_count($path_query);
print "result count: $count\n";

## print the result table
my $res = $query_service->get_result($path_query);
print $res->content();


## now constraint the genus
$path_query->add_constraint('Organism.genus = "Drosophila"');

warn $path_query->to_xml_string();

my $drosophila_count = $query_service->get_count($path_query);
print "Drosophila count: $drosophila_count\n";

my $drosophila_res = $query_service->get_result($path_query);
print $drosophila_res->content();

