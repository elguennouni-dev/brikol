if("serviceWorker" in navigator&&location.protocol==="https:"){navigator.serviceWorker.register("/app/sw.js",{scope:"/app/"}).catch(function(){});}
