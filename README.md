stop-thread
==============

Example on how to update UI from long runnig background threads and how to stop the thread
if it tooks to longi, related to a Vaadin forum [dicussion](https://vaadin.com/forum/#!/thread/16880057).

The background thread takes about 4 seconds to complete, so setting a timeout of 
500 milliseconds the background thread should be interrupted and an error
will be notified; otherwise setting the timeout to 5000 milliseconds or more
the background job should complete succesfully.

Meanwhile the UI will be updated by the bacgkround thread.




Workflow
========

To compile the entire project, run "mvn install".

To run the application, run "mvn jetty:run" and open http://localhost:8080/ .

