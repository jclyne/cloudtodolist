package com.oci.example.todolist.client;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.List;

/**
 * Entry point classes define <code>onModuleLoad()</code>
 */
public class TodoList implements EntryPoint {

    /**
     * The key provider that allows us to identify Contacts even if a field
     * changes. We identify contacts by their unique ID.
     */
    private static final ProvidesKey<TodoListEntry> todoListEntryKeyProvider = new ProvidesKey<TodoListEntry>() {
        public Object getKey(TodoListEntry entry) {
            return entry.getId();
        }
    };

    private final SingleSelectionModel<TodoListEntry> singleEntrySelectionModel = new SingleSelectionModel<TodoListEntry>();

    private final VerticalPanel mainPanel = new VerticalPanel();
    private final CellTable<TodoListEntry> todoList = new CellTable<TodoListEntry>(todoListEntryKeyProvider);
    private final HorizontalPanel toolPanel = new HorizontalPanel();
    private final Button refreshListButton = new Button("Refresh");
    private final Button clearCompletedButton = new Button("Clear Completed");

    private TodoListDataClient client;
    private final int POLL_INTERVAL=5000;

    public void onModuleLoad() {
        client = new TodoListDataClient(new ClientRequestCallback() {
            @Override
            public void onSuccess() {
                redrawTable();
            }

            @Override
            public void onError(String errStr) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        }

        );

        todoList.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.ENABLED);
        todoList.setSelectionModel(singleEntrySelectionModel);
        todoList.addColumn(buildCheckCompleteColumn());
        todoList.addColumn(buildEntryTitleColumn(), "Todo List");

        // Listen for mouse events on the Add button.
        refreshListButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                client.refreshTodoListEntries();
            }
        });

        toolPanel.add(refreshListButton);
        toolPanel.add(clearCompletedButton);

        mainPanel.add(todoList);
        mainPanel.add(toolPanel);

        // Add it to the root panel.
        RootPanel.get().add(mainPanel);

        client.refreshTodoListEntries();

        // Setup timer to refresh list automatically.
/*        Timer refreshTimer = new Timer() {
          @Override
          public void run() {
            client.refreshTodoListEntries();
            redrawTable();
          }
        };
        refreshTimer.scheduleRepeating(POLL_INTERVAL);*/
    }

    void redrawTable() {
        List<TodoListEntry> entryData = client.getCurrentTodoListEntries();

        todoList.setRowCount(entryData.size(), true);
        todoList.setRowData(0, entryData);
        todoList.redraw();
    }

    private Column<TodoListEntry, ?> buildCheckCompleteColumn() {
        // Create the column, mapping a TodoListEntry to the boolean
        Column<TodoListEntry, Boolean> column =
                new Column<TodoListEntry, Boolean>(new CheckboxCell()) {
                    @Override
                    public Boolean getValue(TodoListEntry entry) {
                        return entry.isComplete();
                    }
                };

        // Add a field updater to be notified when the user clears an item
        column.setFieldUpdater(new FieldUpdater<TodoListEntry, Boolean>() {
            public void update(int index, TodoListEntry entry, Boolean value) {
                client.setEntryComplete(entry.getId(),value);
            }
        });

        return column;

    }

    private Column<TodoListEntry, ?> buildEntryTitleColumn() {
        // Create a modifiable title cell
        Column<TodoListEntry, String> column =
                new Column<TodoListEntry, String>(new TextInputCell()) {

                    @Override
                    public String getValue(TodoListEntry entry) {
                        return entry.getTitle();
                    }
                };

        // Add a field updater to be notified when the user enters a new name.
        column.setFieldUpdater(new FieldUpdater<TodoListEntry, String>() {
            public void update(int index, TodoListEntry entry, String value) {

                //client.updateTodoListEntry(entry);

                // Redraw the table with the new data.
                todoList.redraw();
            }
        });

        return column;
    }
}
