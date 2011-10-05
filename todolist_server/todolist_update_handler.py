
from google.appengine.api import channel

# List of connected channel clients
client_list= set()

def add_update_client(client_id):
    """ Adds a client id to the client list """
    client_list.add(client_id)

def remove_update_client(client_id):
    """ Removes a client id to the client list """
    if client_id in client_list:
        client_list.remove(client_id)

def send_update(message):
    """ Sends the specfied updated message to all clients in the client_list """
    for client_id in client_list:
        channel.send_message(client_id,message)
  