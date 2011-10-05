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
            {
                timestamp: timestamp to be used in a get with a modified time
                entries:
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
            }

    Status Codes:
        200(ok) - request was successful
        201(created) - new entry as been created
        400(bad request) - invalid query string
        410(gone) - entry does not exist


    URIs:
        todolist/entries - list of todolist entries
            GET
                Format - todolist_entry array
                Query Parameters = id,modified (e.g. '?id=1;id=3;id=5' or "?modified=1317532850.83)
                    NOTE: Omitting the id parameter will retrieve all entries
                    NOTE: When a modified flag is used, deleted entries may be returned with a
                          deleted flag=true
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

from time import time
import logging

from google.appengine.api import channel
from google.appengine.api.datastore import Key
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.ext import db
from django.utils import simplejson as json

from todolist_update_handler import *


__author__ = "Jeff Clyne"
__copyright__ = "Copyright 2011, Object Computing Inc."
__credits__ = ["Jeff Clyne"]
__license__ = "GPL"
__version__ = "1.0"
__maintainer__ = "Jeff Clyne"
__email__ = "jeffclyne@mindless.com"
__status__ = "released"



# Make duration to archive deleted entries. The deleted
#  entries are necessary for Gets on the entrylist with a
#  modified time
archive_duration = 86400.0 # 24 hours

class TodolistEntry(db.Model):
    """
    Data model for a TodoList Entry
    Also contains static methods for transactional operations on data
    of this kind.
    """

    id = db.IntegerProperty(required=True, default=0)
    title = db.StringProperty(required=True)
    notes = db.StringProperty(multiline=True)
    complete = db.BooleanProperty(required=True, default=False)
    created = db.FloatProperty(required=True)
    modified = db.FloatProperty(required=True)
    deleted = db.BooleanProperty(required=True, default=False)

    @staticmethod
    def create(title, notes=None, complete=None):
        """
        Creates a new entry with the specified title and optional values
        The item is created and then the generated ID from the key is stored
        in the 'id' field. This is done atomically in a transaction to guarantee that
        the 'id' field is never 0.
        """
        entry = TodolistEntry(
            id=0,
            title=title,
            created=now,
            modified=now)
        if notes: entry.notes = notes
        if complete: entry.complete = not int(complete) == 0

        def put_and_update_id_tx():
            entry.id = entry.put().id()
            return entry.put()


        put_and_update_id_tx()
        return entry

    @classmethod
    def update(cls, id, title=None, notes=None, complete=None):
        """
        Updates the specified fields in the entity with the specified id.
        This will atomically update only the specified fields and update
        the modified time.
        """

        def update_tx():
            entry = db.get(Key.from_path(cls.__name__, int(id)))
            if not entry or entry.deleted:
                return None

            entry.modified = now
            if title: entry.title = title
            if notes: entry.notes = notes
            if complete: entry.complete = not int(complete) == 0
            entry.put()
            return entry

        return db.run_in_transaction(update_tx)

    @classmethod
    def mark_deleted(cls, id):
        """
        When entries are 'deleted', the delete flag is marked.
        This allows for update lists that contain deleted entries. A
        cron job will delete all marked entries that are older than the
        archival cutoff time.   This will also update the modified time.
        """

        def mark_deleted_tx():
            entry = db.get(Key.from_path(cls.__name__, int(id)))
            if not entry or entry.deleted:
                return  None

            entry.modified = now
            entry.deleted = True
            entry.put()
            return entry

        return db.run_in_transaction(mark_deleted_tx)


    def to_dict(self):
        """
        Converts the data model to a dictionary.
        This is primarily used as input to the JSON encoder
        """
        return {"id": self.id,
                "title": self.title,
                "notes": self.notes,
                "complete": self.complete,
                "created": self.created,
                "modified": self.modified,
                "deleted": self.deleted}


# Global timestamp that can be referenced anywhere in the package and is
#  updated by the update_timestamp method decorator
now = time()

def update_timestamp(method):
    """  Method wrapper that will update a global timestamp to the current time
        before calling the URI handler method. Add this decoration if there is a
        need for the handler to reference the current time.
    """

    #noinspection PyUnusedLocal
    def wrapper(self, *args, **kwargs):
        global now
        now = time()
        method(self, *args, **kwargs)

    return wrapper


def encode_json(data):
    """  Encodes the specfied data structure into JSON"""
    return json.dumps(data, indent=2)


class EntryListHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist? URL"""

    @update_timestamp
    def get(self):
        """Retrieves the list of todolist entries,

        URI Params:
            None

        Query String Params:
            id - entry id to include in result
                NOTE: this parameter can be specified multiple times

            modified - include all entries modified after specified timestamp
                NOTE: this should usually be a timestamp previously returned in a GET
                NOTE:  since the archived "deleted" entries   are only saved for 24 hours,
                       a modified  timestamp more than 24 hours in the past will fail
                       with 400 status

        Status Codes:
            200(ok) - ok, body includes the todolist_entry list
            400(bad request) - invalid query string  specified
        """

        ids = self.request.get_all("id")
        modified = self.request.get("modified", None)

        query = TodolistEntry.all()

        if modified:
            modified = float(modified)
            if ( now - modified ) > archive_duration:
                logging.error("Modified param '%f' is older than archive duration"%modified)
                self.error(400)
                return

            query.filter("modified >", modified)
            query.filter("modified <=", now)
        else:
            query.filter("deleted =", False)
            query.order("created")

        if ids:
            query.filter("id IN", [int(id) for id in ids])

        self.response.headers['Content-type'] = 'application/json'
        body = encode_json({"timestamp": now, "entries": tuple([r.to_dict() for r in tuple(query)])})
        self.response.out.write(body)

    @update_timestamp
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
            entry = TodolistEntry.create(title=self.request.get("title", None),
                                         notes=self.request.get("notes", None),
                                         complete=self.request.get("complete", None))

            self.response.set_status(201)
            self.response.headers['Content-type'] = 'application/json'
            body = encode_json(entry.to_dict())
            self.response.out.write(body)
            send_update(body)

        except db.BadValueError,e:
            logging.error("Invalid parameter in POST: "+str(e))
            self.error(400)

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
                entry = TodolistEntry.mark_deleted(id)
                if entry:
                    send_update(encode_json(entry.to_dict()))
        else:
            for entry in TodolistEntry.all():
                entry = TodolistEntry.mark_deleted(entry.id)
                if entry:
                    send_update(encode_json(entry.to_dict()))


class EntryHandler(webapp.RequestHandler):
    """ Servlet to handle the /todolist/entries/(\d+) URL"""

    @update_timestamp
    def get(self, id):
        """Returns a single todolist_entry, specified by id

        URI Params:
            id - entry id of the entry to return

        Status Codes:
            200(ok) - ok, body includes the todolist_entry
            410(gone) - entry with specified id does not exist
        """
        entry = db.get(Key.from_path("TodolistEntry", int(id)))
        if entry and not entry.deleted:
            self.response.headers['Content-type'] = 'application/json'
            body = encode_json(entry.to_dict())
            self.response.out.write(body)
        else:
            self.error(410)


    @update_timestamp
    def put(self, id, **params):
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
                                     title=self.request.get("title", None),
                                     notes=self.request.get("notes", None),
                                     complete=self.request.get("complete", None))
        if entry:
            self.response.headers['Content-type'] = 'application/json'
            body = encode_json(entry.to_dict())
            self.response.out.write(body)
            send_update(body)
        else:
            self.error(410)


    @update_timestamp
    def delete(self, id):
        """Deletes the specified entry

        URI Params:
            id - entry id of the entry to delete

        Status Codes:
            200(ok) - ok, body is empty
        """

        entry = TodolistEntry.mark_deleted(id)
        if entry:
            send_update(entry.to_dict())
        else:
            self.error(410)


class CleanArchiveHandler(webapp.RequestHandler):
    """ Servlet that handles notification of the clean archive cron job"""

    def get(self):
        """  Task request to clean archived deleted entries

        """
        query = TodolistEntry.all()
        query.filter("deleted", True)
        count=0
        for entry in query:
            count+=1
            entry.delete()

        logging.info("CleanArchiveHandler removed %d deleted entries"%count)


class ChannelHandler(webapp.RequestHandler):
    """ Servlet to handle update channel token requests"""

    def get (self):
        """Returns a single channel token that can
            be used by a client to connect to for updates

        URI Params:
            none

        Status Codes:
            200(ok) - ok, body includes the channel token
        """

        # creates a token that is generated by a client_id, which is
        # the source ip address and the current time in milliseconds
        remote_addr = self.request.remote_addr
        logging.info("Client update channel request from "+remote_addr)
        token = channel.create_channel(remote_addr + str(now))

        self.response.headers['Content-type'] = 'application/json'
        body = encode_json({"token": token})
        self.response.out.write(body)


class ChannelConnectHandler(webapp.RequestHandler):
    """ Servlet that handles notification of a channel connect """

    def post(self):
        """ Stores the client_id of the client that connected
            to a channel. Once stored, updates will be sent
            out, via the channel to this client

            Note: the post is genereted, on this URI, from the channel
                  module to indicate a channel connect. The 'from'
                  header has the client_id used to generatethe channel
                  token

            URI Params:
                none

            Status Codes:
                200(ok) - ok

        """

        client_id = self.request.get('from')
        logging.info("Connecting client update channel "+client_id)
        add_update_client(client_id)


class ChannelDisconnectHandler(webapp.RequestHandler):
    """ Servlet that handles notification of a channel disconnect """

    def post(self):
        """ Stores the client_id of the client that disconnected
            to a channel.

            Note: the post is genereted, on this URI, from the channel
                  module to indicate a channel disconnect. The 'from'
                  header has the client_id used to generatethe channel
                  token

            URI Params:
                none

            Status Codes:
                200(ok) - ok

        """
        client_id= self.request.get('from')
        logging.info("Disconnecting client update channel "+client_id)
        remove_update_client(client_id)


def main():
    logging.getLogger().setLevel(logging.INFO)

    app = webapp.WSGIApplication([('/todolist/entries', EntryListHandler),
                                  ('/todolist/entries/(\d+)', EntryHandler),
                                  ('/todolist/tasks/clean_archive', CleanArchiveHandler),
                                  ('/todolist/update_channel', ChannelHandler),
                                  ('/_ah/channel/connected/', ChannelConnectHandler),
                                  ('/_ah/channel/disconnected/', ChannelDisconnectHandler)],
                                 debug=True)

    run_wsgi_app(app)

if __name__ == '__main__':
    main()
