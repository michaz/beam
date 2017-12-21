#!/bin/bash
echo "Generating experiments for config: $1"
java -cp build/libs/beam.jar beam.experiment.ExperimentGenerator --experiments $1

echo "Experiments generated in: $(dirname $1)"
IFS=$'\n' read -d '' -r -a experiments < $(dirname $1)/experiments.csv

#for exp in "${experiments[@]}"
for (( i=1; i<${#experiments[@]}; i++ ));
do
    IFS=', ' read -r -a csv_rows <<< "${experiments[$i]}"
    base_dir=${csv_rows[1]//\\//}
    echo "Running experiment using config $base_dir/beam.conf"
    if [ "$2" == "cloud" ]; then
        $base_dir/runExperiment.sh cloud  > $base_dir/console.log 2>&1
    else
        $base_dir/runExperiment.sh > $base_dir/console.log 2>&1
    fi
done
unset experiments
