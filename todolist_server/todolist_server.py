#!/usr/bin/python


""" Restful Web Protocol for Todo List
  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    Copyright (C) 2011  Object Computing Inc

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    This implements a RESTful web protocol to manage a todo list. All the todo list
    entries are persisted in a relational database store.  Resource responses are
    encoded in JSON.

    Protocol Definition:

    Data Format:
        todolist_entry object
            {
              "id": entry ID,
              "title": title of the Entry,
              "notes": notes associated with the entry,
              "complete": flag indicating whether the entry is complete
            }
        todolist_entry array
            [
              {
                "id": entry ID,
                "title": title of the Entry,
                "notes": notes associated with the entry,
                "complete": flag indicating whether the entry is complete
              },
              {
                "id": entry ID,
                "title": title of the Entry,
                "notes": notes associated with the entry,
                "complete": flag indicating whether the entry is complete
              },
              ...
            ]

    Status Codes:
        200(ok) - request was successful
        201(created) - new entry as been created
        400(bad request) - invalid query string
        410(gone) - entry does not exist


    URIs:
        todolist/entries - list of todolist entries
            GET
                Format - todolist_entry array
                Query Parameters = id (e.g. '?id=1' or '?id=1+2+5')
                    NOTE: Omitting the id parameter will retrieve all entries
                Status Codes - 200,400

            POST
                Format - todolist_entry
                Query Parameters = title,notes,completed (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
                Status Codes - 201,400

            DELETE
                Format - empty
                Query Parameters = id (e.g. '?id=1' or '?id=1+2+5')
                Status Codes - 200
                
        todolist/entries/<id> - single todolist entry, referenced by entry id
            GET
                Format - todolist_entry
                Status Codes - 200,410

            PUT
                Format - todolist_entry
                Query Parameters = title,notes,completed (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
                Status Codes - 200,400,410

            DELETE
                Format - empty
                Status Codes - 200
                
"""


from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.ext import db
from django.utils import simplejson as json
from time import time
from threading import Thread,Event


__author__ = "Jeff Clyne"
__copyright__ = "Copyright 2011, Object Computing Inc."
__credits__ = ["Jeff Clyne"]
__license__ = "GPL"
__version__ = "1.0"
__maintainer__ = "Jeff Clyne"
__email__ = "jeffclyne@mindless.com"
__status__= "released"



archive_duration=86400.0 # 24 hours
cleanup_interval=1800.0 # 30 minutes


class Index(webapp.RequestHandler):
    """ Servlet to handle a redirect on the base module URL"""
    def GET(self):
        pass


def sqlors(key,values):
    return " OR ".join([key+" = "+value for value in values])

class TodolistEntry(db.Model):
    title = db.StringProperty(required=True)
    notes = db.StringProperty()
    completed = db.BooleanProperty(required=True,default=False)
    created = db.FloatProperty(required=True)
    modified = db.FloatProperty(required=True)
    deleted = db.BooleanProperty(required=True,default=False)

    def to_dict(self):
       return dict(self.__dict__)

class EntryListHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist? URL"""

    def get(self):
        """Retrieves the list of todolist entries,

        URI Params:
            None

        Query String Params:
            id - entry id to include in result
                NOTE: this parameter can have multiple values

        Status Codes:
            200(ok) - ok, body includes the todolist_entry list
            400(bad request) - invalid query string  specified
        """

        now = time()
        ids = self.request.get_all("id")
        modified = self.request.get("modified",None)
        where=None

        if modified:
            if ( now - float(modified) ) > archive_duration:
                raise self.error(400)

            where="(modified > %s AND modified <= %f)"%(modified,now)
        else:
            where="deleted = False"

        if ids:
            where+=" AND ("+sqlors('id',ids)+")"

        query = db.GqlQuery("SELECT * from TodolistEntry "+
                            "WHERE "+where+ " "+
                            "ORDER BY created ")

        res = tuple(query)
        entries = tuple([r.to_dict() for r in res])

        self.response.headers['Content-type'] = 'application/json'
        self.response.out.write(
                json.dumps({"timestamp":now, "entries":entries}
                           ,indent=2) )

        self.response.set_status(200)

    def post(self):
        """Creates a new todolist entry

        URI Params:
            None

        Query String Params:
            title - string contaning entry title
            notes - string contaning entry notes
            complete - boolean flag indicating the entry is completed

        Status Codes:
            201(created) -  body includes the new todolist_entry
            400(bad request) - invalid query string  specified
        """

        now = time()

        entry = TodolistEntry(title = self.request.get("title",None),
                                notes = self.request.get("notes",None),
                                completed = self.request.get("completed",False),
                                created=now,modified=now)

        newid = entry.put()

        self.response.headers['Content-type'] = 'application/json'
        self.response.out.write({"id":newid.id()})
        self.response.set_status(201)

    def delete(self,id=None):
        """Deletes the specified list of entries

        URI Params:
            none

        Query String Params:
            id - entry id to delete
                NOTE: this parameter can have multiple values
                NOTE: If omitted, all todolist entries will be deleted

        Status Codes:
            200(ok) - ok, body is empty
        """
        pass
        
class EntryHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist/entries/(\d+) URL"""

    def get(self,id):
        """Returns a single todolist_entry, specified by id

        URI Params:
            id - entry id of the entry to return

        Status Codes:
            200(ok) - ok, body includes the todolist_entry
            410(gone) - entry with specified id does not exist
        """
        pass


    def put(self,id,**params):
        """Updates the specified entry, with the values specified
        in the query string, and returns the updated todolist_entry

        URI Params:
            id - entry id of the entry to update

        Query String Params:
            title - string contaning entry title
            notes - string contaning entry notes
            complete - boolean flag indicating the entry is completed

        Status Codes:
            200(ok) - ok, body includes the updated todolist_entry
            400(bad request) - invalid query string  specified
            410(gone) - entry with specified id does not exist
        """

        pass

    def delete(self,id):
        """Deletes the specified entry

        URI Params:
            id - entry id of the entry to delete

        Status Codes:
            200(ok) - ok, body is empty
        """

        pass


app = webapp.WSGIApplication([('/todolist', Index),
                              ('/todolist/entries', EntryListHandler),
                              ('/todolist/entries/(\d+)',EntryHandler)],
                             debug=True)

if __name__ == '__main__':
    run_wsgi_app(app)
