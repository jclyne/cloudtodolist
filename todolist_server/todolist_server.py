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
        todolist/entry/<id> - single todolist entry, referenced by entry id
            GET
                Format - todolist_entry
                Status Codes - 200,410

            PUT
                Format - todolist_entry
                Status Codes - 200,400,410

            DELETE
                Format - empty
                Status Codes - 200

        todolist/entrylist? - list of todolist entries
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
"""

import web
import json

__author__ = "Jeff Clyne"
__copyright__ = "Copyright 2011, Object Computing Inc."
__credits__ = ["Jeff Clyne"]
__license__ = "GPL"
__version__ = "1.0"
__maintainer__ = "Jeff Clyne"
__email__ = "jeffclyne@mindless.com"
__status__= "released"

urls = (
    '/todolist', 'Index',
    '/todolist/entry/(\d+)', 'EntryHandler',
    '/todolist/entrylist', 'EntryListHandler'
)

app=web.application(urls, globals())

users_table="users"
entries_table="entries"
db = web.database(
    dbn='mysql',
    db='todolist',
    user='root',
    passwd="Vel0city"
)

class encode_response(object):
    """ Method decorator that will encode returned responses, as well as
    data in exceptions, into wire format
    """

    @staticmethod
    def encode_json(object):
        """ Encodes the specified object into JSON, using the
         builtin JSON encoder. It also writes the correct content-type
         into the response header
        """
        web.header('Content-Type', 'application/json')
        if isinstance(object,web.utils.IterBetter):
            object=tuple(object)

        out=  json.dumps(object,indent=2)
        return out

    def __init__(self,encode_type):
        self.encoder=getattr(self,"encode_"+encode_type)

    def __call__(self,method):
        def encode_wrapper(*args,**kwargs):
            try:
                return self.encoder(method(*args,**kwargs))
            except web.HTTPError,e:
                if hasattr(e,'data'):
                    e.data=self.encoder(e.data)
                raise e

        return encode_wrapper

class parse_web_input(object):
    """ Method decorator to parse the incoming web input, validate it,
    and pass the values as keyword arguments to the wrapped method. The
    valid params are passed in a list as well as a flag indicating whether
    and empty parameter list is valid
    """

    @staticmethod
    def decode_id(value):
        return tuple([int(id) for id in value.split()])

    @staticmethod
    def decode_title(value):
        return value

    @staticmethod
    def decode_notes(value):
        return value

    @staticmethod
    def decode_complete(value):
        flag = int(value)
        if flag: return 1
        return 0

    def __init__(self,valid_params=(),valid_empty=True):
        assert(type(valid_empty) is bool)

        self.valid_params=set(valid_params)
        self.valid_empty=valid_empty

    def __call__(self,method):
        def validate_wrapper(*args,**kwargs):
            input=dict(web.input())
            if not (input or self.valid_empty): raise web.badrequest()
            if not (set(input.keys()) <= self.valid_params): raise web.badrequest()

            for key,value in input.items():
                try:
                    input[key]=getattr(self,"decode_"+key)(value)
                except AttributeError:
                    pass

            kwargs.update(input)
            return method(*args,**kwargs)

        return validate_wrapper

class Index(object):
    """ Servlet to handle a redirect on the base module URL"""
    def GET(self):
        raise web.seeother("static/todolist.html")


class EntryHandler(object):
    """ Servlet to handle the /todolist/api/entry/(\d+) URL"""

    @encode_response('json')
    def GET(self,id):
        """Returns a single todolist_entry, specified by id

        URI Params:
            id - entry id of the entry to return

        Status Codes:
            200(ok) - ok, body includes the todolist_entry
            410(gone) - entry with specified id does not exist
        """
        try:
            return db.where(entries_table,what='id,title,notes,complete',id=id)[0]
        except IndexError:
            raise web.gone()

    @encode_response('json')
    @parse_web_input(('title', 'notes', 'complete'),False)
    def PUT(self,id,**params):
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

        with db.transaction():
            db.update(entries_table,
                      where='id=$id',
                      vars=locals(),
                      **params)
            try:
                return db.where(entries_table,  id=id )[0]
            except IndexError:
                raise web.gone()

    def DELETE(self,id):
        """Deletes the specified entry

        URI Params:
            id - entry id of the entry to delete

        Status Codes:
            200(ok) - ok, body is empty
        """
        db.delete(entries_table, where='$id = id',vars=locals())


class EntryListHandler(object):
    """ Servlet to handle the /todolist/api/entrylist? URL"""


    @encode_response('json')
    @parse_web_input(('id',))
    def GET(self,id=None):
        """Retrieves the list of todolist entries,

        URI Params:
            None

        Query String Params:
            id - entry id to include in result
                NOTE: this parameter can have multiple values
                NOTE: If omitted, all todolist entries will be returned

        Status Codes:
            200(ok) - ok, body includes the todolist_entry list
            400(bad request) - invalid query string  specified
        """

        if id:
            where=web.db.sqlors('id = ',id)
        else:
            where=None

        return db.select(entries_table,order='id', where=where)

    @encode_response('json')
    @parse_web_input(('title', 'notes', 'complete'))
    def POST(self,**params):
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

        with db.transaction():
            new_entry= db.where( entries_table, id=db.insert( entries_table, **params ) )[0]

        raise web.created(new_entry)

    @parse_web_input(('id',),False)
    def DELETE(self,id=None):
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
        if id:
            where=web.db.sqlors('id = ',id)
        else:
            where=None

        db.delete(entries_table, where=where)

if __name__ == '__main__':
    app.run()
