#!/usr/local/bin/Rscript
##############################################################################################################################################
# Script to convert CSV files to an R data table and then save as Rdata. This is intended to be run from the project root directory.
##############################################################################################################################################
#setwd('/Users/critter/Dropbox/ucb/vto/beam-all/beam') # for development and debugging
source('./src/main/R/beam-utilities.R')

##############################################################################################################################################
# LOAD LIBRARIES NEED BY THIS SCRIPT
load.libraries(c('optparse'),quietly=T)

##############################################################################################################################################
# COMMAND LINE OPTIONS 
option_list <- list(
)
if(interactive()){
  #setwd('~/downs/')
  args<-'/Users/critter/Documents/matsim/pev/development_2016-07-19_19-28-47/ITERS/it.0/run0.0.events.csv'
  args <- parse_args(OptionParser(option_list = option_list,usage = "csv2Rdata.R [file-to-convert]"),positional_arguments=T,args=args)
}else{
  args <- parse_args(OptionParser(option_list = option_list,usage = "csv2Rdata.R [file-to-convert]"),positional_arguments=T)
}

######################################################################################################
file.path <- args$args[1]
for(file.path in args$args){
  path <- str_split(file.path,"/")[[1]]
  if(length(path)>1){
    the.file <- tail(path,1)
    the.dir <- pp(pp(head(path,-1),collapse='/'),'/')
  }else{
    the.file <- path
    the.dir <- './'
  }
  df <- csv2rdata(the.file)
}
