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
                Query Parameters = title,notes,complete (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
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
                Query Parameters = title,notes,complete (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
                Status Codes - 200,400,410

            DELETE
                Format - empty
                Status Codes - 200
                
"""
from google.appengine.api.datastore import Key
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.ext import db
from django.utils import simplejson as json
from time import time


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
now=time()

class Index(webapp.RequestHandler):
    """ Servlet to handle a redirect on the base module URL"""
    def GET(self):
        pass


def sqlors(key,values):
    return " OR ".join([key+" = "+value for value in values])

class TodolistEntry(db.Model):
    id = db.IntegerProperty(required=True,default=0)
    title = db.StringProperty(required=True)
    notes = db.StringProperty()
    complete = db.BooleanProperty(required=True,default=False)
    created = db.FloatProperty(required=True)
    modified = db.FloatProperty(required=True)
    deleted = db.BooleanProperty(required=True,default=False)

    @staticmethod
    def create(title,notes=None,complete=None):
        entry = TodolistEntry(
                              id=0,
                              title=title,
                              created=now,
                              modified=now)
        if notes: entry.notes=notes
        if complete: entry.complete= not int(complete)==0

        def put_and_update_id_tx():
            entry.id = entry.put().id()
            return entry.put()


        put_and_update_id_tx()
        return entry

    @classmethod
    def update(cls,id,title=None,notes=None,complete=None):
        def update_tx():
            entry = db.get(Key.from_path(cls.__name__, int(id)))
            if not entry or entry.deleted:
                return None

            entry.modified=now
            if title: entry.title=title
            if notes: entry.notes=notes
            if complete: entry.complete=not int(complete)==0
            entry.put()
            return entry

        return db.run_in_transaction(update_tx)

    @classmethod
    def mark_deleted(cls,id):
        def update_tx():
            entry = db.get(Key.from_path(cls.__name__, int(id)))
            if not entry or entry.deleted:
                return  None

            entry.modified=now
            entry.deleted=True
            entry.put()
            return entry

        return db.run_in_transaction(update_tx)


    def to_dict(self):
       return {"id" : self.id,
               "title" : self.title,
               "notes" : self.notes,
               "complete" : self.complete,
               "created" : self.created,
               "modified" : self.modified,
               "deleted" : self.deleted}


def update_timestamp(method):
    def wrapper(self,*args,**kwargs):
        global now
        now=time()
        method(self,*args,**kwargs)
    return wrapper


def encode_json_response(method):
    def wrapper(self,*args,**kwargs):
        result = method(self,*args,**kwargs)
        if type(result) is tuple:
            status,body = result
            if status >=200 and status < 300:
                self.response.headers['Content-type'] = 'application/json'
                self.response.out.write(json.dumps(body,indent=2))
                self.response.set_status(status)
            else:
                self.error(status)
        else:
            self.error(result)

    return wrapper

class EntryListHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist? URL"""

    @update_timestamp
    @encode_json_response
    def get(self):
        """Retrieves the list of todolist entries,

        URI Params:
            None

        Query String Params:
            id - entry id to include in result
                NOTE: this parameter can be specified multiple times

            modified - include all entries modified after specified timestamp
                NOTE: this should usually be a timestamp previously returned in a GET

        Status Codes:
            200(ok) - ok, body includes the todolist_entry list
            400(bad request) - invalid query string  specified
        """



        ids = self.request.get_all("id")
        modified = self.request.get("modified",None)

        query = TodolistEntry.all()

        if modified:
            modified = float(modified)
            if ( now - modified ) > archive_duration:
                return 400

            query.filter("modified >",modified)
            query.filter("modified <=",now)
        else:
            query.filter("deleted =",False)
            query.order("created")

        if ids:
            query.filter("id IN",[int(id) for id in ids])

        return 200,{"timestamp":now,"entries":tuple([r.to_dict() for r in tuple(query)])}

    @update_timestamp
    @encode_json_response
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

        try:
            entry = TodolistEntry.create( title = self.request.get("title",None),
                                          notes = self.request.get("notes",None),
                                          complete = self.request.get("complete",None) )
        except db.BadValueError:
            return 400

        return 201,entry.to_dict()

    @update_timestamp
    def delete(self):
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

        ids = self.request.get_all("id")
        if ids:
            for id in ids:
                TodolistEntry.mark_deleted(id)
        else:
            for entry in TodolistEntry.all():
                TodolistEntry.mark_deleted(entry.id)

class EntryHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist/entries/(\d+) URL"""

    @update_timestamp
    @encode_json_response
    def get(self,id):
        """Returns a single todolist_entry, specified by id

        URI Params:
            id - entry id of the entry to return

        Status Codes:
            200(ok) - ok, body includes the todolist_entry
            410(gone) - entry with specified id does not exist
        """
        entry = db.get(Key.from_path("TodolistEntry", int(id)))
        if not entry or entry.deleted:
            return 410

        return 200,entry.to_dict()


    @update_timestamp
    @encode_json_response
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


        entry = TodolistEntry.update(id,
                                    title = self.request.get("title",None),
                                      notes = self.request.get("notes",None),
                                      complete = self.request.get("complete",None))
        if not entry:
            return 410
        return 200,entry.to_dict()



    @update_timestamp
    def delete(self,id):
        """Deletes the specified entry

        URI Params:
            id - entry id of the entry to delete

        Status Codes:
            200(ok) - ok, body is empty
        """

        if not TodolistEntry.mark_deleted(id):
            return 410
        return 200


app = webapp.WSGIApplication([('/todolist', Index),
                              ('/todolist/entries', EntryListHandler),
                              ('/todolist/entries/(\d+)',EntryHandler)],
                             debug=True)

if __name__ == '__main__':
    run_wsgi_app(app)
