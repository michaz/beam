#!/bin/bash
export MAXRAM=1920g
./gradlew run -PappArgs="['--config', 'production/application-sfbay/sfBay_transit_price_low.conf']"
#./gradlew run -PappArgs="['--config', 'production/application-sfbay/sfBay_transit_capacity_low.conf']"
#./gradlew run -PappArgs="['--config', 'production/application-sfbay/sfBay_transit_price_high.conf']"
