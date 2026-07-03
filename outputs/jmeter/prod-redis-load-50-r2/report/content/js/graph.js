/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
$(document).ready(function() {

    $(".click-title").mouseenter( function(    e){
        e.preventDefault();
        this.style.cursor="pointer";
    });
    $(".click-title").mousedown( function(event){
        event.preventDefault();
    });

    // Ugly code while this script is shared among several pages
    try{
        refreshHitsPerSecond(true);
    } catch(e){}
    try{
        refreshResponseTimeOverTime(true);
    } catch(e){}
    try{
        refreshResponseTimePercentiles();
    } catch(e){}
});


var responseTimePercentilesInfos = {
        data: {"result": {"minY": 120904.0, "minX": 0.0, "maxY": 127393.0, "series": [{"data": [[0.0, 120904.0], [0.1, 120904.0], [0.2, 120904.0], [0.3, 120904.0], [0.4, 120904.0], [0.5, 120904.0], [0.6, 120904.0], [0.7, 120904.0], [0.8, 120904.0], [0.9, 120904.0], [1.0, 120904.0], [1.1, 120904.0], [1.2, 120904.0], [1.3, 120904.0], [1.4, 120904.0], [1.5, 120904.0], [1.6, 120904.0], [1.7, 120904.0], [1.8, 120904.0], [1.9, 120904.0], [2.0, 121001.0], [2.1, 121001.0], [2.2, 121001.0], [2.3, 121001.0], [2.4, 121001.0], [2.5, 121001.0], [2.6, 121001.0], [2.7, 121001.0], [2.8, 121001.0], [2.9, 121001.0], [3.0, 121001.0], [3.1, 121001.0], [3.2, 121001.0], [3.3, 121001.0], [3.4, 121001.0], [3.5, 121001.0], [3.6, 121001.0], [3.7, 121001.0], [3.8, 121001.0], [3.9, 121001.0], [4.0, 123369.0], [4.1, 123369.0], [4.2, 123369.0], [4.3, 123369.0], [4.4, 123369.0], [4.5, 123369.0], [4.6, 123369.0], [4.7, 123369.0], [4.8, 123369.0], [4.9, 123369.0], [5.0, 123369.0], [5.1, 123369.0], [5.2, 123369.0], [5.3, 123369.0], [5.4, 123369.0], [5.5, 123369.0], [5.6, 123369.0], [5.7, 123369.0], [5.8, 123369.0], [5.9, 123369.0], [6.0, 123432.0], [6.1, 123432.0], [6.2, 123432.0], [6.3, 123432.0], [6.4, 123432.0], [6.5, 123432.0], [6.6, 123432.0], [6.7, 123432.0], [6.8, 123432.0], [6.9, 123432.0], [7.0, 123432.0], [7.1, 123432.0], [7.2, 123432.0], [7.3, 123432.0], [7.4, 123432.0], [7.5, 123432.0], [7.6, 123432.0], [7.7, 123432.0], [7.8, 123432.0], [7.9, 123432.0], [8.0, 123478.0], [8.1, 123478.0], [8.2, 123478.0], [8.3, 123478.0], [8.4, 123478.0], [8.5, 123478.0], [8.6, 123478.0], [8.7, 123478.0], [8.8, 123478.0], [8.9, 123478.0], [9.0, 123478.0], [9.1, 123478.0], [9.2, 123478.0], [9.3, 123478.0], [9.4, 123478.0], [9.5, 123478.0], [9.6, 123478.0], [9.7, 123478.0], [9.8, 123478.0], [9.9, 123478.0], [10.0, 123507.0], [10.1, 123507.0], [10.2, 123507.0], [10.3, 123507.0], [10.4, 123507.0], [10.5, 123507.0], [10.6, 123507.0], [10.7, 123507.0], [10.8, 123507.0], [10.9, 123507.0], [11.0, 123507.0], [11.1, 123507.0], [11.2, 123507.0], [11.3, 123507.0], [11.4, 123507.0], [11.5, 123507.0], [11.6, 123507.0], [11.7, 123507.0], [11.8, 123507.0], [11.9, 123507.0], [12.0, 123526.0], [12.1, 123526.0], [12.2, 123526.0], [12.3, 123526.0], [12.4, 123526.0], [12.5, 123526.0], [12.6, 123526.0], [12.7, 123526.0], [12.8, 123526.0], [12.9, 123526.0], [13.0, 123526.0], [13.1, 123526.0], [13.2, 123526.0], [13.3, 123526.0], [13.4, 123526.0], [13.5, 123526.0], [13.6, 123526.0], [13.7, 123526.0], [13.8, 123526.0], [13.9, 123526.0], [14.0, 123576.0], [14.1, 123576.0], [14.2, 123576.0], [14.3, 123576.0], [14.4, 123576.0], [14.5, 123576.0], [14.6, 123576.0], [14.7, 123576.0], [14.8, 123576.0], [14.9, 123576.0], [15.0, 123576.0], [15.1, 123576.0], [15.2, 123576.0], [15.3, 123576.0], [15.4, 123576.0], [15.5, 123576.0], [15.6, 123576.0], [15.7, 123576.0], [15.8, 123576.0], [15.9, 123576.0], [16.0, 123625.0], [16.1, 123625.0], [16.2, 123625.0], [16.3, 123625.0], [16.4, 123625.0], [16.5, 123625.0], [16.6, 123625.0], [16.7, 123625.0], [16.8, 123625.0], [16.9, 123625.0], [17.0, 123625.0], [17.1, 123625.0], [17.2, 123625.0], [17.3, 123625.0], [17.4, 123625.0], [17.5, 123625.0], [17.6, 123625.0], [17.7, 123625.0], [17.8, 123625.0], [17.9, 123625.0], [18.0, 123674.0], [18.1, 123674.0], [18.2, 123674.0], [18.3, 123674.0], [18.4, 123674.0], [18.5, 123674.0], [18.6, 123674.0], [18.7, 123674.0], [18.8, 123674.0], [18.9, 123674.0], [19.0, 123674.0], [19.1, 123674.0], [19.2, 123674.0], [19.3, 123674.0], [19.4, 123674.0], [19.5, 123674.0], [19.6, 123674.0], [19.7, 123674.0], [19.8, 123674.0], [19.9, 123674.0], [20.0, 123941.0], [20.1, 123941.0], [20.2, 123941.0], [20.3, 123941.0], [20.4, 123941.0], [20.5, 123941.0], [20.6, 123941.0], [20.7, 123941.0], [20.8, 123941.0], [20.9, 123941.0], [21.0, 123941.0], [21.1, 123941.0], [21.2, 123941.0], [21.3, 123941.0], [21.4, 123941.0], [21.5, 123941.0], [21.6, 123941.0], [21.7, 123941.0], [21.8, 123941.0], [21.9, 123941.0], [22.0, 123961.0], [22.1, 123961.0], [22.2, 123961.0], [22.3, 123961.0], [22.4, 123961.0], [22.5, 123961.0], [22.6, 123961.0], [22.7, 123961.0], [22.8, 123961.0], [22.9, 123961.0], [23.0, 123961.0], [23.1, 123961.0], [23.2, 123961.0], [23.3, 123961.0], [23.4, 123961.0], [23.5, 123961.0], [23.6, 123961.0], [23.7, 123961.0], [23.8, 123961.0], [23.9, 123961.0], [24.0, 124009.0], [24.1, 124009.0], [24.2, 124009.0], [24.3, 124009.0], [24.4, 124009.0], [24.5, 124009.0], [24.6, 124009.0], [24.7, 124009.0], [24.8, 124009.0], [24.9, 124009.0], [25.0, 124009.0], [25.1, 124009.0], [25.2, 124009.0], [25.3, 124009.0], [25.4, 124009.0], [25.5, 124009.0], [25.6, 124009.0], [25.7, 124009.0], [25.8, 124009.0], [25.9, 124009.0], [26.0, 124092.0], [26.1, 124092.0], [26.2, 124092.0], [26.3, 124092.0], [26.4, 124092.0], [26.5, 124092.0], [26.6, 124092.0], [26.7, 124092.0], [26.8, 124092.0], [26.9, 124092.0], [27.0, 124092.0], [27.1, 124092.0], [27.2, 124092.0], [27.3, 124092.0], [27.4, 124092.0], [27.5, 124092.0], [27.6, 124092.0], [27.7, 124092.0], [27.8, 124092.0], [27.9, 124092.0], [28.0, 124098.0], [28.1, 124098.0], [28.2, 124098.0], [28.3, 124098.0], [28.4, 124098.0], [28.5, 124098.0], [28.6, 124098.0], [28.7, 124098.0], [28.8, 124098.0], [28.9, 124098.0], [29.0, 124098.0], [29.1, 124098.0], [29.2, 124098.0], [29.3, 124098.0], [29.4, 124098.0], [29.5, 124098.0], [29.6, 124098.0], [29.7, 124098.0], [29.8, 124098.0], [29.9, 124098.0], [30.0, 124099.0], [30.1, 124099.0], [30.2, 124099.0], [30.3, 124099.0], [30.4, 124099.0], [30.5, 124099.0], [30.6, 124099.0], [30.7, 124099.0], [30.8, 124099.0], [30.9, 124099.0], [31.0, 124099.0], [31.1, 124099.0], [31.2, 124099.0], [31.3, 124099.0], [31.4, 124099.0], [31.5, 124099.0], [31.6, 124099.0], [31.7, 124099.0], [31.8, 124099.0], [31.9, 124099.0], [32.0, 124117.0], [32.1, 124117.0], [32.2, 124117.0], [32.3, 124117.0], [32.4, 124117.0], [32.5, 124117.0], [32.6, 124117.0], [32.7, 124117.0], [32.8, 124117.0], [32.9, 124117.0], [33.0, 124117.0], [33.1, 124117.0], [33.2, 124117.0], [33.3, 124117.0], [33.4, 124117.0], [33.5, 124117.0], [33.6, 124117.0], [33.7, 124117.0], [33.8, 124117.0], [33.9, 124117.0], [34.0, 124131.0], [34.1, 124131.0], [34.2, 124131.0], [34.3, 124131.0], [34.4, 124131.0], [34.5, 124131.0], [34.6, 124131.0], [34.7, 124131.0], [34.8, 124131.0], [34.9, 124131.0], [35.0, 124131.0], [35.1, 124131.0], [35.2, 124131.0], [35.3, 124131.0], [35.4, 124131.0], [35.5, 124131.0], [35.6, 124131.0], [35.7, 124131.0], [35.8, 124131.0], [35.9, 124131.0], [36.0, 124219.0], [36.1, 124219.0], [36.2, 124219.0], [36.3, 124219.0], [36.4, 124219.0], [36.5, 124219.0], [36.6, 124219.0], [36.7, 124219.0], [36.8, 124219.0], [36.9, 124219.0], [37.0, 124219.0], [37.1, 124219.0], [37.2, 124219.0], [37.3, 124219.0], [37.4, 124219.0], [37.5, 124219.0], [37.6, 124219.0], [37.7, 124219.0], [37.8, 124219.0], [37.9, 124219.0], [38.0, 124239.0], [38.1, 124239.0], [38.2, 124239.0], [38.3, 124239.0], [38.4, 124239.0], [38.5, 124239.0], [38.6, 124239.0], [38.7, 124239.0], [38.8, 124239.0], [38.9, 124239.0], [39.0, 124239.0], [39.1, 124239.0], [39.2, 124239.0], [39.3, 124239.0], [39.4, 124239.0], [39.5, 124239.0], [39.6, 124239.0], [39.7, 124239.0], [39.8, 124239.0], [39.9, 124239.0], [40.0, 124358.0], [40.1, 124358.0], [40.2, 124358.0], [40.3, 124358.0], [40.4, 124358.0], [40.5, 124358.0], [40.6, 124358.0], [40.7, 124358.0], [40.8, 124358.0], [40.9, 124358.0], [41.0, 124358.0], [41.1, 124358.0], [41.2, 124358.0], [41.3, 124358.0], [41.4, 124358.0], [41.5, 124358.0], [41.6, 124358.0], [41.7, 124358.0], [41.8, 124358.0], [41.9, 124358.0], [42.0, 124500.0], [42.1, 124500.0], [42.2, 124500.0], [42.3, 124500.0], [42.4, 124500.0], [42.5, 124500.0], [42.6, 124500.0], [42.7, 124500.0], [42.8, 124500.0], [42.9, 124500.0], [43.0, 124500.0], [43.1, 124500.0], [43.2, 124500.0], [43.3, 124500.0], [43.4, 124500.0], [43.5, 124500.0], [43.6, 124500.0], [43.7, 124500.0], [43.8, 124500.0], [43.9, 124500.0], [44.0, 124532.0], [44.1, 124532.0], [44.2, 124532.0], [44.3, 124532.0], [44.4, 124532.0], [44.5, 124532.0], [44.6, 124532.0], [44.7, 124532.0], [44.8, 124532.0], [44.9, 124532.0], [45.0, 124532.0], [45.1, 124532.0], [45.2, 124532.0], [45.3, 124532.0], [45.4, 124532.0], [45.5, 124532.0], [45.6, 124532.0], [45.7, 124532.0], [45.8, 124532.0], [45.9, 124532.0], [46.0, 124568.0], [46.1, 124568.0], [46.2, 124568.0], [46.3, 124568.0], [46.4, 124568.0], [46.5, 124568.0], [46.6, 124568.0], [46.7, 124568.0], [46.8, 124568.0], [46.9, 124568.0], [47.0, 124568.0], [47.1, 124568.0], [47.2, 124568.0], [47.3, 124568.0], [47.4, 124568.0], [47.5, 124568.0], [47.6, 124568.0], [47.7, 124568.0], [47.8, 124568.0], [47.9, 124568.0], [48.0, 124636.0], [48.1, 124636.0], [48.2, 124636.0], [48.3, 124636.0], [48.4, 124636.0], [48.5, 124636.0], [48.6, 124636.0], [48.7, 124636.0], [48.8, 124636.0], [48.9, 124636.0], [49.0, 124636.0], [49.1, 124636.0], [49.2, 124636.0], [49.3, 124636.0], [49.4, 124636.0], [49.5, 124636.0], [49.6, 124636.0], [49.7, 124636.0], [49.8, 124636.0], [49.9, 124636.0], [50.0, 124692.0], [50.1, 124692.0], [50.2, 124692.0], [50.3, 124692.0], [50.4, 124692.0], [50.5, 124692.0], [50.6, 124692.0], [50.7, 124692.0], [50.8, 124692.0], [50.9, 124692.0], [51.0, 124692.0], [51.1, 124692.0], [51.2, 124692.0], [51.3, 124692.0], [51.4, 124692.0], [51.5, 124692.0], [51.6, 124692.0], [51.7, 124692.0], [51.8, 124692.0], [51.9, 124692.0], [52.0, 124736.0], [52.1, 124736.0], [52.2, 124736.0], [52.3, 124736.0], [52.4, 124736.0], [52.5, 124736.0], [52.6, 124736.0], [52.7, 124736.0], [52.8, 124736.0], [52.9, 124736.0], [53.0, 124736.0], [53.1, 124736.0], [53.2, 124736.0], [53.3, 124736.0], [53.4, 124736.0], [53.5, 124736.0], [53.6, 124736.0], [53.7, 124736.0], [53.8, 124736.0], [53.9, 124736.0], [54.0, 124997.0], [54.1, 124997.0], [54.2, 124997.0], [54.3, 124997.0], [54.4, 124997.0], [54.5, 124997.0], [54.6, 124997.0], [54.7, 124997.0], [54.8, 124997.0], [54.9, 124997.0], [55.0, 124997.0], [55.1, 124997.0], [55.2, 124997.0], [55.3, 124997.0], [55.4, 124997.0], [55.5, 124997.0], [55.6, 124997.0], [55.7, 124997.0], [55.8, 124997.0], [55.9, 124997.0], [56.0, 125113.0], [56.1, 125113.0], [56.2, 125113.0], [56.3, 125113.0], [56.4, 125113.0], [56.5, 125113.0], [56.6, 125113.0], [56.7, 125113.0], [56.8, 125113.0], [56.9, 125113.0], [57.0, 125113.0], [57.1, 125113.0], [57.2, 125113.0], [57.3, 125113.0], [57.4, 125113.0], [57.5, 125113.0], [57.6, 125113.0], [57.7, 125113.0], [57.8, 125113.0], [57.9, 125113.0], [58.0, 125135.0], [58.1, 125135.0], [58.2, 125135.0], [58.3, 125135.0], [58.4, 125135.0], [58.5, 125135.0], [58.6, 125135.0], [58.7, 125135.0], [58.8, 125135.0], [58.9, 125135.0], [59.0, 125135.0], [59.1, 125135.0], [59.2, 125135.0], [59.3, 125135.0], [59.4, 125135.0], [59.5, 125135.0], [59.6, 125135.0], [59.7, 125135.0], [59.8, 125135.0], [59.9, 125135.0], [60.0, 125163.0], [60.1, 125163.0], [60.2, 125163.0], [60.3, 125163.0], [60.4, 125163.0], [60.5, 125163.0], [60.6, 125163.0], [60.7, 125163.0], [60.8, 125163.0], [60.9, 125163.0], [61.0, 125163.0], [61.1, 125163.0], [61.2, 125163.0], [61.3, 125163.0], [61.4, 125163.0], [61.5, 125163.0], [61.6, 125163.0], [61.7, 125163.0], [61.8, 125163.0], [61.9, 125163.0], [62.0, 125324.0], [62.1, 125324.0], [62.2, 125324.0], [62.3, 125324.0], [62.4, 125324.0], [62.5, 125324.0], [62.6, 125324.0], [62.7, 125324.0], [62.8, 125324.0], [62.9, 125324.0], [63.0, 125324.0], [63.1, 125324.0], [63.2, 125324.0], [63.3, 125324.0], [63.4, 125324.0], [63.5, 125324.0], [63.6, 125324.0], [63.7, 125324.0], [63.8, 125324.0], [63.9, 125324.0], [64.0, 125455.0], [64.1, 125455.0], [64.2, 125455.0], [64.3, 125455.0], [64.4, 125455.0], [64.5, 125455.0], [64.6, 125455.0], [64.7, 125455.0], [64.8, 125455.0], [64.9, 125455.0], [65.0, 125455.0], [65.1, 125455.0], [65.2, 125455.0], [65.3, 125455.0], [65.4, 125455.0], [65.5, 125455.0], [65.6, 125455.0], [65.7, 125455.0], [65.8, 125455.0], [65.9, 125455.0], [66.0, 125474.0], [66.1, 125474.0], [66.2, 125474.0], [66.3, 125474.0], [66.4, 125474.0], [66.5, 125474.0], [66.6, 125474.0], [66.7, 125474.0], [66.8, 125474.0], [66.9, 125474.0], [67.0, 125474.0], [67.1, 125474.0], [67.2, 125474.0], [67.3, 125474.0], [67.4, 125474.0], [67.5, 125474.0], [67.6, 125474.0], [67.7, 125474.0], [67.8, 125474.0], [67.9, 125474.0], [68.0, 125523.0], [68.1, 125523.0], [68.2, 125523.0], [68.3, 125523.0], [68.4, 125523.0], [68.5, 125523.0], [68.6, 125523.0], [68.7, 125523.0], [68.8, 125523.0], [68.9, 125523.0], [69.0, 125523.0], [69.1, 125523.0], [69.2, 125523.0], [69.3, 125523.0], [69.4, 125523.0], [69.5, 125523.0], [69.6, 125523.0], [69.7, 125523.0], [69.8, 125523.0], [69.9, 125523.0], [70.0, 125873.0], [70.1, 125873.0], [70.2, 125873.0], [70.3, 125873.0], [70.4, 125873.0], [70.5, 125873.0], [70.6, 125873.0], [70.7, 125873.0], [70.8, 125873.0], [70.9, 125873.0], [71.0, 125873.0], [71.1, 125873.0], [71.2, 125873.0], [71.3, 125873.0], [71.4, 125873.0], [71.5, 125873.0], [71.6, 125873.0], [71.7, 125873.0], [71.8, 125873.0], [71.9, 125873.0], [72.0, 125927.0], [72.1, 125927.0], [72.2, 125927.0], [72.3, 125927.0], [72.4, 125927.0], [72.5, 125927.0], [72.6, 125927.0], [72.7, 125927.0], [72.8, 125927.0], [72.9, 125927.0], [73.0, 125927.0], [73.1, 125927.0], [73.2, 125927.0], [73.3, 125927.0], [73.4, 125927.0], [73.5, 125927.0], [73.6, 125927.0], [73.7, 125927.0], [73.8, 125927.0], [73.9, 125927.0], [74.0, 126173.0], [74.1, 126173.0], [74.2, 126173.0], [74.3, 126173.0], [74.4, 126173.0], [74.5, 126173.0], [74.6, 126173.0], [74.7, 126173.0], [74.8, 126173.0], [74.9, 126173.0], [75.0, 126173.0], [75.1, 126173.0], [75.2, 126173.0], [75.3, 126173.0], [75.4, 126173.0], [75.5, 126173.0], [75.6, 126173.0], [75.7, 126173.0], [75.8, 126173.0], [75.9, 126173.0], [76.0, 126184.0], [76.1, 126184.0], [76.2, 126184.0], [76.3, 126184.0], [76.4, 126184.0], [76.5, 126184.0], [76.6, 126184.0], [76.7, 126184.0], [76.8, 126184.0], [76.9, 126184.0], [77.0, 126184.0], [77.1, 126184.0], [77.2, 126184.0], [77.3, 126184.0], [77.4, 126184.0], [77.5, 126184.0], [77.6, 126184.0], [77.7, 126184.0], [77.8, 126184.0], [77.9, 126184.0], [78.0, 126199.0], [78.1, 126199.0], [78.2, 126199.0], [78.3, 126199.0], [78.4, 126199.0], [78.5, 126199.0], [78.6, 126199.0], [78.7, 126199.0], [78.8, 126199.0], [78.9, 126199.0], [79.0, 126199.0], [79.1, 126199.0], [79.2, 126199.0], [79.3, 126199.0], [79.4, 126199.0], [79.5, 126199.0], [79.6, 126199.0], [79.7, 126199.0], [79.8, 126199.0], [79.9, 126199.0], [80.0, 126282.0], [80.1, 126282.0], [80.2, 126282.0], [80.3, 126282.0], [80.4, 126282.0], [80.5, 126282.0], [80.6, 126282.0], [80.7, 126282.0], [80.8, 126282.0], [80.9, 126282.0], [81.0, 126282.0], [81.1, 126282.0], [81.2, 126282.0], [81.3, 126282.0], [81.4, 126282.0], [81.5, 126282.0], [81.6, 126282.0], [81.7, 126282.0], [81.8, 126282.0], [81.9, 126282.0], [82.0, 126340.0], [82.1, 126340.0], [82.2, 126340.0], [82.3, 126340.0], [82.4, 126340.0], [82.5, 126340.0], [82.6, 126340.0], [82.7, 126340.0], [82.8, 126340.0], [82.9, 126340.0], [83.0, 126340.0], [83.1, 126340.0], [83.2, 126340.0], [83.3, 126340.0], [83.4, 126340.0], [83.5, 126340.0], [83.6, 126340.0], [83.7, 126340.0], [83.8, 126340.0], [83.9, 126340.0], [84.0, 126426.0], [84.1, 126426.0], [84.2, 126426.0], [84.3, 126426.0], [84.4, 126426.0], [84.5, 126426.0], [84.6, 126426.0], [84.7, 126426.0], [84.8, 126426.0], [84.9, 126426.0], [85.0, 126426.0], [85.1, 126426.0], [85.2, 126426.0], [85.3, 126426.0], [85.4, 126426.0], [85.5, 126426.0], [85.6, 126426.0], [85.7, 126426.0], [85.8, 126426.0], [85.9, 126426.0], [86.0, 126482.0], [86.1, 126482.0], [86.2, 126482.0], [86.3, 126482.0], [86.4, 126482.0], [86.5, 126482.0], [86.6, 126482.0], [86.7, 126482.0], [86.8, 126482.0], [86.9, 126482.0], [87.0, 126482.0], [87.1, 126482.0], [87.2, 126482.0], [87.3, 126482.0], [87.4, 126482.0], [87.5, 126482.0], [87.6, 126482.0], [87.7, 126482.0], [87.8, 126482.0], [87.9, 126482.0], [88.0, 126498.0], [88.1, 126498.0], [88.2, 126498.0], [88.3, 126498.0], [88.4, 126498.0], [88.5, 126498.0], [88.6, 126498.0], [88.7, 126498.0], [88.8, 126498.0], [88.9, 126498.0], [89.0, 126498.0], [89.1, 126498.0], [89.2, 126498.0], [89.3, 126498.0], [89.4, 126498.0], [89.5, 126498.0], [89.6, 126498.0], [89.7, 126498.0], [89.8, 126498.0], [89.9, 126498.0], [90.0, 126621.0], [90.1, 126621.0], [90.2, 126621.0], [90.3, 126621.0], [90.4, 126621.0], [90.5, 126621.0], [90.6, 126621.0], [90.7, 126621.0], [90.8, 126621.0], [90.9, 126621.0], [91.0, 126621.0], [91.1, 126621.0], [91.2, 126621.0], [91.3, 126621.0], [91.4, 126621.0], [91.5, 126621.0], [91.6, 126621.0], [91.7, 126621.0], [91.8, 126621.0], [91.9, 126621.0], [92.0, 126949.0], [92.1, 126949.0], [92.2, 126949.0], [92.3, 126949.0], [92.4, 126949.0], [92.5, 126949.0], [92.6, 126949.0], [92.7, 126949.0], [92.8, 126949.0], [92.9, 126949.0], [93.0, 126949.0], [93.1, 126949.0], [93.2, 126949.0], [93.3, 126949.0], [93.4, 126949.0], [93.5, 126949.0], [93.6, 126949.0], [93.7, 126949.0], [93.8, 126949.0], [93.9, 126949.0], [94.0, 127038.0], [94.1, 127038.0], [94.2, 127038.0], [94.3, 127038.0], [94.4, 127038.0], [94.5, 127038.0], [94.6, 127038.0], [94.7, 127038.0], [94.8, 127038.0], [94.9, 127038.0], [95.0, 127038.0], [95.1, 127038.0], [95.2, 127038.0], [95.3, 127038.0], [95.4, 127038.0], [95.5, 127038.0], [95.6, 127038.0], [95.7, 127038.0], [95.8, 127038.0], [95.9, 127038.0], [96.0, 127055.0], [96.1, 127055.0], [96.2, 127055.0], [96.3, 127055.0], [96.4, 127055.0], [96.5, 127055.0], [96.6, 127055.0], [96.7, 127055.0], [96.8, 127055.0], [96.9, 127055.0], [97.0, 127055.0], [97.1, 127055.0], [97.2, 127055.0], [97.3, 127055.0], [97.4, 127055.0], [97.5, 127055.0], [97.6, 127055.0], [97.7, 127055.0], [97.8, 127055.0], [97.9, 127055.0], [98.0, 127393.0], [98.1, 127393.0], [98.2, 127393.0], [98.3, 127393.0], [98.4, 127393.0], [98.5, 127393.0], [98.6, 127393.0], [98.7, 127393.0], [98.8, 127393.0], [98.9, 127393.0], [99.0, 127393.0], [99.1, 127393.0], [99.2, 127393.0], [99.3, 127393.0], [99.4, 127393.0], [99.5, 127393.0], [99.6, 127393.0], [99.7, 127393.0], [99.8, 127393.0], [99.9, 127393.0]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 100.0, "title": "Response Time Percentiles"}},
        getOptions: function() {
            return {
                series: {
                    points: { show: false }
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentiles'
                },
                xaxis: {
                    tickDecimals: 1,
                    axisLabel: "Percentiles",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Percentile value in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : %x.2 percentile was %y ms"
                },
                selection: { mode: "xy" },
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentiles"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesPercentiles"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesPercentiles"), dataset, prepareOverviewOptions(options));
        }
};

/**
 * @param elementId Id of element where we display message
 */
function setEmptyGraph(elementId) {
    $(function() {
        $(elementId).text("No graph series with filter="+seriesFilter);
    });
}

// Response times percentiles
function refreshResponseTimePercentiles() {
    var infos = responseTimePercentilesInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimePercentiles");
        return;
    }
    if (isGraph($("#flotResponseTimesPercentiles"))){
        infos.createGraph();
    } else {
        var choiceContainer = $("#choicesResponseTimePercentiles");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesPercentiles", "#overviewResponseTimesPercentiles");
        $('#bodyResponseTimePercentiles .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimeDistributionInfos = {
        data: {"result": {"minY": 1.0, "minX": 120900.0, "maxY": 4.0, "series": [{"data": [[120900.0, 1.0], [121000.0, 1.0], [126400.0, 3.0], [124900.0, 1.0], [126100.0, 3.0], [123900.0, 2.0], [123300.0, 1.0], [123400.0, 2.0], [123500.0, 3.0], [123600.0, 2.0], [124200.0, 2.0], [124300.0, 1.0], [124500.0, 3.0], [124600.0, 2.0], [124700.0, 1.0], [125100.0, 3.0], [125300.0, 1.0], [126900.0, 1.0], [126600.0, 1.0], [124000.0, 4.0], [124100.0, 2.0], [126200.0, 1.0], [126300.0, 1.0], [125400.0, 2.0], [125500.0, 1.0], [125800.0, 1.0], [125900.0, 1.0], [127000.0, 2.0], [127300.0, 1.0]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 100, "maxX": 127300.0, "title": "Response Time Distribution"}},
        getOptions: function() {
            var granularity = this.data.result.granularity;
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    barWidth: this.data.result.granularity
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " responses for " + label + " were between " + xval + " and " + (xval + granularity) + " ms";
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimeDistribution"), prepareData(data.result.series, $("#choicesResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshResponseTimeDistribution() {
    var infos = responseTimeDistributionInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeDistribution");
        return;
    }
    if (isGraph($("#flotResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var syntheticResponseTimeDistributionInfos = {
        data: {"result": {"minY": 50.0, "minX": 2.0, "ticks": [[0, "Requests having \nresponse time <= 500ms"], [1, "Requests having \nresponse time > 500ms and <= 1,500ms"], [2, "Requests having \nresponse time > 1,500ms"], [3, "Requests in error"]], "maxY": 50.0, "series": [{"data": [], "color": "#9ACD32", "isOverall": false, "label": "Requests having \nresponse time <= 500ms", "isController": false}, {"data": [], "color": "yellow", "isOverall": false, "label": "Requests having \nresponse time > 500ms and <= 1,500ms", "isController": false}, {"data": [[2.0, 50.0]], "color": "orange", "isOverall": false, "label": "Requests having \nresponse time > 1,500ms", "isController": false}, {"data": [], "color": "#FF6347", "isOverall": false, "label": "Requests in error", "isController": false}], "supportsControllersDiscrimination": false, "maxX": 2.0, "title": "Synthetic Response Times Distribution"}},
        getOptions: function() {
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendSyntheticResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times ranges",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                    tickLength:0,
                    min:-0.5,
                    max:3.5
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    align: "center",
                    barWidth: 0.25,
                    fill:.75
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " " + label;
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            options.xaxis.ticks = data.result.ticks;
            $.plot($("#flotSyntheticResponseTimeDistribution"), prepareData(data.result.series, $("#choicesSyntheticResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshSyntheticResponseTimeDistribution() {
    var infos = syntheticResponseTimeDistributionInfos;
    prepareSeries(infos.data, true);
    if (isGraph($("#flotSyntheticResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerSyntheticResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var activeThreadsOverTimeInfos = {
        data: {"result": {"minY": 2.5, "minX": 1.7829543E12, "maxY": 23.0, "series": [{"data": [[1.78295436E12, 22.700000000000003], [1.78295454E12, 9.0], [1.78295448E12, 18.81818181818182], [1.7829543E12, 23.0], [1.7829546E12, 2.5], [1.78295442E12, 23.0]], "isOverall": false, "label": "100 driver charging journeys", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.7829546E12, "title": "Active Threads Over Time"}},
        getOptions: function() {
            return {
                series: {
                    stack: true,
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 6,
                    show: true,
                    container: '#legendActiveThreadsOverTime'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                selection: {
                    mode: 'xy'
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : At %x there were %y active threads"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesActiveThreadsOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotActiveThreadsOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewActiveThreadsOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Active Threads Over Time
function refreshActiveThreadsOverTime(fixTimestamps) {
    var infos = activeThreadsOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotActiveThreadsOverTime"))) {
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesActiveThreadsOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotActiveThreadsOverTime", "#overviewActiveThreadsOverTime");
        $('#footerActiveThreadsOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var timeVsThreadsInfos = {
        data: {"result": {"minY": 123369.0, "minX": 1.0, "maxY": 126621.0, "series": [{"data": [[8.0, 126426.0], [2.0, 124692.0], [9.0, 126184.0], [10.0, 123674.0], [11.0, 123625.0], [12.0, 123576.0], [3.0, 126621.0], [13.0, 123507.0], [14.0, 125455.0], [15.0, 123526.0], [16.0, 123478.0], [4.0, 124636.0], [1.0, 124736.0], [17.0, 123432.0], [18.0, 123369.0], [19.0, 125324.0], [20.0, 124117.0], [5.0, 124568.0], [21.0, 124358.0], [22.0, 123899.8], [23.0, 125367.04166666667], [6.0, 124532.0], [7.0, 124500.0]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}, {"data": [[17.860000000000003, 124852.87999999999]], "isOverall": false, "label": "Full charging journey with SSE-Aggregated", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 23.0, "title": "Time VS Threads"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: { noColumns: 2,show: true, container: '#legendTimeVsThreads' },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s: At %x.2 active threads, Average response time was %y.2 ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesTimeVsThreads"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotTimesVsThreads"), dataset, options);
            // setup overview
            $.plot($("#overviewTimesVsThreads"), dataset, prepareOverviewOptions(options));
        }
};

// Time vs threads
function refreshTimeVsThreads(){
    var infos = timeVsThreadsInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTimeVsThreads");
        return;
    }
    if(isGraph($("#flotTimesVsThreads"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTimeVsThreads");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTimesVsThreads", "#overviewTimesVsThreads");
        $('#footerTimeVsThreads .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var bytesThroughputOverTimeInfos = {
        data : {"result": {"minY": 0.0, "minX": 1.7829543E12, "maxY": 69.11666666666666, "series": [{"data": [[1.78295436E12, 62.833333333333336], [1.78295454E12, 56.55], [1.78295448E12, 69.11666666666666], [1.7829543E12, 37.7], [1.7829546E12, 25.133333333333333], [1.78295442E12, 62.833333333333336]], "isOverall": false, "label": "Bytes received per second", "isController": false}, {"data": [[1.78295436E12, 0.0], [1.78295454E12, 0.0], [1.78295448E12, 0.0], [1.7829543E12, 0.0], [1.7829546E12, 0.0], [1.78295442E12, 0.0]], "isOverall": false, "label": "Bytes sent per second", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.7829546E12, "title": "Bytes Throughput Over Time"}},
        getOptions : function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity) ,
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Bytes / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendBytesThroughputOverTime'
                },
                selection: {
                    mode: "xy"
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y"
                }
            };
        },
        createGraph : function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesBytesThroughputOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotBytesThroughputOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewBytesThroughputOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Bytes throughput Over Time
function refreshBytesThroughputOverTime(fixTimestamps) {
    var infos = bytesThroughputOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotBytesThroughputOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesBytesThroughputOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotBytesThroughputOverTime", "#overviewBytesThroughputOverTime");
        $('#footerBytesThroughputOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimesOverTimeInfos = {
        data: {"result": {"minY": 124355.45454545454, "minX": 1.7829543E12, "maxY": 125633.0, "series": [{"data": [[1.78295436E12, 124444.70000000001], [1.78295454E12, 124510.2222222222], [1.78295448E12, 124355.45454545454], [1.7829543E12, 125633.0], [1.7829546E12, 125171.25], [1.78295442E12, 125521.2]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.7829546E12, "title": "Response Time Over Time"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average response time was %y ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Times Over Time
function refreshResponseTimeOverTime(fixTimestamps) {
    var infos = responseTimesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotResponseTimesOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesOverTime", "#overviewResponseTimesOverTime");
        $('#footerResponseTimesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var latenciesOverTimeInfos = {
        data: {"result": {"minY": 0.0, "minX": 1.7829543E12, "maxY": 4.9E-324, "series": [{"data": [[1.78295436E12, 0.0], [1.78295454E12, 0.0], [1.78295448E12, 0.0], [1.7829543E12, 0.0], [1.7829546E12, 0.0], [1.78295442E12, 0.0]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.7829546E12, "title": "Latencies Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response latencies in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendLatenciesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average latency was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesLatenciesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotLatenciesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewLatenciesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Latencies Over Time
function refreshLatenciesOverTime(fixTimestamps) {
    var infos = latenciesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyLatenciesOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotLatenciesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesLatenciesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotLatenciesOverTime", "#overviewLatenciesOverTime");
        $('#footerLatenciesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var connectTimeOverTimeInfos = {
        data: {"result": {"minY": 0.0, "minX": 1.7829543E12, "maxY": 4.9E-324, "series": [{"data": [[1.78295436E12, 0.0], [1.78295454E12, 0.0], [1.78295448E12, 0.0], [1.7829543E12, 0.0], [1.7829546E12, 0.0], [1.78295442E12, 0.0]], "isOverall": false, "label": "Full charging journey with SSE", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.7829546E12, "title": "Connect Time Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getConnectTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average Connect Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendConnectTimeOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average connect time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesConnectTimeOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotConnectTimeOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewConnectTimeOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Connect Time Over Time
function refreshConnectTimeOverTime(fixTimestamps) {
    var infos = connectTimeOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyConnectTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotConnectTimeOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesConnectTimeOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotConnectTimeOverTime", "#overviewConnectTimeOverTime");
        $('#footerConnectTimeOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var responseTimePercentilesOverTimeInfos = {
        data: {"result": {"minY": 120904.0, "minX": 1.7829543E12, "maxY": 127393.0, "series": [{"data": [[1.78295436E12, 127038.0], [1.78295454E12, 126426.0], [1.78295448E12, 126949.0], [1.7829543E12, 127055.0], [1.7829546E12, 126621.0], [1.78295442E12, 127393.0]], "isOverall": false, "label": "Max", "isController": false}, {"data": [[1.78295436E12, 126984.0], [1.78295454E12, 126426.0], [1.78295448E12, 126650.2], [1.7829543E12, 127055.0], [1.7829546E12, 126621.0], [1.78295442E12, 127301.9]], "isOverall": false, "label": "90th percentile", "isController": false}, {"data": [[1.78295436E12, 127038.0], [1.78295454E12, 126426.0], [1.78295448E12, 126949.0], [1.7829543E12, 127055.0], [1.7829546E12, 126621.0], [1.78295442E12, 127393.0]], "isOverall": false, "label": "99th percentile", "isController": false}, {"data": [[1.78295436E12, 127038.0], [1.78295454E12, 126426.0], [1.78295448E12, 126949.0], [1.7829543E12, 127055.0], [1.7829546E12, 126621.0], [1.78295442E12, 127393.0]], "isOverall": false, "label": "95th percentile", "isController": false}, {"data": [[1.78295436E12, 120904.0], [1.78295454E12, 123507.0], [1.78295448E12, 123369.0], [1.7829543E12, 124239.0], [1.7829546E12, 124636.0], [1.78295442E12, 124009.0]], "isOverall": false, "label": "Min", "isController": false}, {"data": [[1.78295436E12, 124666.0], [1.78295454E12, 124500.0], [1.78295448E12, 123961.0], [1.7829543E12, 125654.0], [1.7829546E12, 124714.0], [1.78295442E12, 125698.0]], "isOverall": false, "label": "Median", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.7829546E12, "title": "Response Time Percentiles Over Time (successful requests only)"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Response Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentilesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Response time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentilesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimePercentilesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimePercentilesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Time Percentiles Over Time
function refreshResponseTimePercentilesOverTime(fixTimestamps) {
    var infos = responseTimePercentilesOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotResponseTimePercentilesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimePercentilesOverTime", "#overviewResponseTimePercentilesOverTime");
        $('#footerResponseTimePercentilesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var responseTimeVsRequestInfos = {
    data: {"result": {"minY": 124664.0, "minX": 1.0, "maxY": 124664.0, "series": [{"data": [[1.0, 124664.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 1.0, "title": "Response Time Vs Request"}},
    getOptions: function() {
        return {
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Response Time in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: {
                noColumns: 2,
                show: true,
                container: '#legendResponseTimeVsRequest'
            },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median response time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesResponseTimeVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotResponseTimeVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewResponseTimeVsRequest"), dataset, prepareOverviewOptions(options));

    }
};

// Response Time vs Request
function refreshResponseTimeVsRequest() {
    var infos = responseTimeVsRequestInfos;
    prepareSeries(infos.data);
    if (isGraph($("#flotResponseTimeVsRequest"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeVsRequest");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimeVsRequest", "#overviewResponseTimeVsRequest");
        $('#footerResponseRimeVsRequest .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var latenciesVsRequestInfos = {
    data: {"result": {"minY": 0.0, "minX": 1.0, "maxY": 4.9E-324, "series": [{"data": [[1.0, 0.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 1.0, "title": "Latencies Vs Request"}},
    getOptions: function() {
        return{
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Latency in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: { noColumns: 2,show: true, container: '#legendLatencyVsRequest' },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median Latency time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesLatencyVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotLatenciesVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewLatenciesVsRequest"), dataset, prepareOverviewOptions(options));
    }
};

// Latencies vs Request
function refreshLatenciesVsRequest() {
        var infos = latenciesVsRequestInfos;
        prepareSeries(infos.data);
        if(isGraph($("#flotLatenciesVsRequest"))){
            infos.createGraph();
        }else{
            var choiceContainer = $("#choicesLatencyVsRequest");
            createLegend(choiceContainer, infos);
            infos.createGraph();
            setGraphZoomable("#flotLatenciesVsRequest", "#overviewLatenciesVsRequest");
            $('#footerLatenciesVsRequest .legendColorBox > div').each(function(i){
                $(this).clone().prependTo(choiceContainer.find("li").eq(i));
            });
        }
};

var hitsPerSecondInfos = {
        data: {"result": {"minY": 0.05, "minX": 1.78295418E12, "maxY": 0.16666666666666666, "series": [{"data": [[1.78295436E12, 0.16666666666666666], [1.78295448E12, 0.05], [1.78295418E12, 0.11666666666666667], [1.7829543E12, 0.16666666666666666], [1.78295424E12, 0.16666666666666666], [1.78295442E12, 0.16666666666666666]], "isOverall": false, "label": "hitsPerSecond", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.78295448E12, "title": "Hits Per Second"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of hits / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendHitsPerSecond"
                },
                selection: {
                    mode : 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y.2 hits/sec"
                }
            };
        },
        createGraph: function createGraph() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesHitsPerSecond"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotHitsPerSecond"), dataset, options);
            // setup overview
            $.plot($("#overviewHitsPerSecond"), dataset, prepareOverviewOptions(options));
        }
};

// Hits per second
function refreshHitsPerSecond(fixTimestamps) {
    var infos = hitsPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if (isGraph($("#flotHitsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesHitsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotHitsPerSecond", "#overviewHitsPerSecond");
        $('#footerHitsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var codesPerSecondInfos = {
        data: {"result": {"minY": 0.06666666666666667, "minX": 1.7829543E12, "maxY": 0.18333333333333332, "series": [{"data": [[1.78295436E12, 0.16666666666666666], [1.78295454E12, 0.15], [1.78295448E12, 0.18333333333333332], [1.7829543E12, 0.1], [1.7829546E12, 0.06666666666666667], [1.78295442E12, 0.16666666666666666]], "isOverall": false, "label": "200", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.7829546E12, "title": "Codes Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendCodesPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "Number of Response Codes %s at %x was %y.2 responses / sec"
                }
            };
        },
    createGraph: function() {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesCodesPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotCodesPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewCodesPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Codes per second
function refreshCodesPerSecond(fixTimestamps) {
    var infos = codesPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotCodesPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesCodesPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotCodesPerSecond", "#overviewCodesPerSecond");
        $('#footerCodesPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var transactionsPerSecondInfos = {
        data: {"result": {"minY": 0.06666666666666667, "minX": 1.7829543E12, "maxY": 0.18333333333333332, "series": [{"data": [[1.78295436E12, 0.16666666666666666], [1.78295454E12, 0.15], [1.78295448E12, 0.18333333333333332], [1.7829543E12, 0.1], [1.7829546E12, 0.06666666666666667], [1.78295442E12, 0.16666666666666666]], "isOverall": false, "label": "Full charging journey with SSE-success", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.7829546E12, "title": "Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTransactionsPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                }
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTransactionsPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTransactionsPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewTransactionsPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Transactions per second
function refreshTransactionsPerSecond(fixTimestamps) {
    var infos = transactionsPerSecondInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTransactionsPerSecond");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotTransactionsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTransactionsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTransactionsPerSecond", "#overviewTransactionsPerSecond");
        $('#footerTransactionsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var totalTPSInfos = {
        data: {"result": {"minY": 0.06666666666666667, "minX": 1.7829543E12, "maxY": 0.18333333333333332, "series": [{"data": [[1.78295436E12, 0.16666666666666666], [1.78295454E12, 0.15], [1.78295448E12, 0.18333333333333332], [1.7829543E12, 0.1], [1.7829546E12, 0.06666666666666667], [1.78295442E12, 0.16666666666666666]], "isOverall": false, "label": "Transaction-success", "isController": false}, {"data": [], "isOverall": false, "label": "Transaction-failure", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.7829546E12, "title": "Total Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTotalTPS"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                },
                colors: ["#9ACD32", "#FF6347"]
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTotalTPS"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTotalTPS"), dataset, options);
        // setup overview
        $.plot($("#overviewTotalTPS"), dataset, prepareOverviewOptions(options));
    }
};

// Total Transactions per second
function refreshTotalTPS(fixTimestamps) {
    var infos = totalTPSInfos;
    // We want to ignore seriesFilter
    prepareSeries(infos.data, false, true);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 0);
    }
    if(isGraph($("#flotTotalTPS"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTotalTPS");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTotalTPS", "#overviewTotalTPS");
        $('#footerTotalTPS .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

// Collapse the graph matching the specified DOM element depending the collapsed
// status
function collapse(elem, collapsed){
    if(collapsed){
        $(elem).parent().find(".fa-chevron-up").removeClass("fa-chevron-up").addClass("fa-chevron-down");
    } else {
        $(elem).parent().find(".fa-chevron-down").removeClass("fa-chevron-down").addClass("fa-chevron-up");
        if (elem.id == "bodyBytesThroughputOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshBytesThroughputOverTime(true);
            }
            document.location.href="#bytesThroughputOverTime";
        } else if (elem.id == "bodyLatenciesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesOverTime(true);
            }
            document.location.href="#latenciesOverTime";
        } else if (elem.id == "bodyCustomGraph") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCustomGraph(true);
            }
            document.location.href="#responseCustomGraph";
        } else if (elem.id == "bodyConnectTimeOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshConnectTimeOverTime(true);
            }
            document.location.href="#connectTimeOverTime";
        } else if (elem.id == "bodyResponseTimePercentilesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimePercentilesOverTime(true);
            }
            document.location.href="#responseTimePercentilesOverTime";
        } else if (elem.id == "bodyResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeDistribution();
            }
            document.location.href="#responseTimeDistribution" ;
        } else if (elem.id == "bodySyntheticResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshSyntheticResponseTimeDistribution();
            }
            document.location.href="#syntheticResponseTimeDistribution" ;
        } else if (elem.id == "bodyActiveThreadsOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshActiveThreadsOverTime(true);
            }
            document.location.href="#activeThreadsOverTime";
        } else if (elem.id == "bodyTimeVsThreads") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTimeVsThreads();
            }
            document.location.href="#timeVsThreads" ;
        } else if (elem.id == "bodyCodesPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCodesPerSecond(true);
            }
            document.location.href="#codesPerSecond";
        } else if (elem.id == "bodyTransactionsPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTransactionsPerSecond(true);
            }
            document.location.href="#transactionsPerSecond";
        } else if (elem.id == "bodyTotalTPS") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTotalTPS(true);
            }
            document.location.href="#totalTPS";
        } else if (elem.id == "bodyResponseTimeVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeVsRequest();
            }
            document.location.href="#responseTimeVsRequest";
        } else if (elem.id == "bodyLatenciesVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesVsRequest();
            }
            document.location.href="#latencyVsRequest";
        }
    }
}

/*
 * Activates or deactivates all series of the specified graph (represented by id parameter)
 * depending on checked argument.
 */
function toggleAll(id, checked){
    var placeholder = document.getElementById(id);

    var cases = $(placeholder).find(':checkbox');
    cases.prop('checked', checked);
    $(cases).parent().children().children().toggleClass("legend-disabled", !checked);

    var choiceContainer;
    if ( id == "choicesBytesThroughputOverTime"){
        choiceContainer = $("#choicesBytesThroughputOverTime");
        refreshBytesThroughputOverTime(false);
    } else if(id == "choicesResponseTimesOverTime"){
        choiceContainer = $("#choicesResponseTimesOverTime");
        refreshResponseTimeOverTime(false);
    }else if(id == "choicesResponseCustomGraph"){
        choiceContainer = $("#choicesResponseCustomGraph");
        refreshCustomGraph(false);
    } else if ( id == "choicesLatenciesOverTime"){
        choiceContainer = $("#choicesLatenciesOverTime");
        refreshLatenciesOverTime(false);
    } else if ( id == "choicesConnectTimeOverTime"){
        choiceContainer = $("#choicesConnectTimeOverTime");
        refreshConnectTimeOverTime(false);
    } else if ( id == "choicesResponseTimePercentilesOverTime"){
        choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        refreshResponseTimePercentilesOverTime(false);
    } else if ( id == "choicesResponseTimePercentiles"){
        choiceContainer = $("#choicesResponseTimePercentiles");
        refreshResponseTimePercentiles();
    } else if(id == "choicesActiveThreadsOverTime"){
        choiceContainer = $("#choicesActiveThreadsOverTime");
        refreshActiveThreadsOverTime(false);
    } else if ( id == "choicesTimeVsThreads"){
        choiceContainer = $("#choicesTimeVsThreads");
        refreshTimeVsThreads();
    } else if ( id == "choicesSyntheticResponseTimeDistribution"){
        choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        refreshSyntheticResponseTimeDistribution();
    } else if ( id == "choicesResponseTimeDistribution"){
        choiceContainer = $("#choicesResponseTimeDistribution");
        refreshResponseTimeDistribution();
    } else if ( id == "choicesHitsPerSecond"){
        choiceContainer = $("#choicesHitsPerSecond");
        refreshHitsPerSecond(false);
    } else if(id == "choicesCodesPerSecond"){
        choiceContainer = $("#choicesCodesPerSecond");
        refreshCodesPerSecond(false);
    } else if ( id == "choicesTransactionsPerSecond"){
        choiceContainer = $("#choicesTransactionsPerSecond");
        refreshTransactionsPerSecond(false);
    } else if ( id == "choicesTotalTPS"){
        choiceContainer = $("#choicesTotalTPS");
        refreshTotalTPS(false);
    } else if ( id == "choicesResponseTimeVsRequest"){
        choiceContainer = $("#choicesResponseTimeVsRequest");
        refreshResponseTimeVsRequest();
    } else if ( id == "choicesLatencyVsRequest"){
        choiceContainer = $("#choicesLatencyVsRequest");
        refreshLatenciesVsRequest();
    }
    var color = checked ? "black" : "#818181";
    if(choiceContainer != null) {
        choiceContainer.find("label").each(function(){
            this.style.color = color;
        });
    }
}

