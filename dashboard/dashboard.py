"""
A Streamlit dashboard application for real-time monitoring of a TCP server.

This module defines the Dashboard class, which fetches monitoring statistics from a server endpoint,
logs the data, and visualizes key metrics using interactive charts within a web interface.

Dependencies:
    - pandas
    - plotly
    - requests
    - streamlit
    - streamlit_autorefresh
"""

import os
from typing import Dict
import pandas as pd
import plotly.express as px
from plotly.graph_objs import Figure
import requests
import streamlit as st
from streamlit_autorefresh import st_autorefresh


class Dashboard:
    """
    A Streamlit-based dashboard for visualizing real-time monitoring statistics of a TCP server.

    This class handles the initialization of the dashboard, fetching and logging monitoring data,
    and rendering interactive charts for key server metrics.

    Attributes:
        CONF (dict): Streamlit page configuration settings.
        HOST (str): The Server host address.
        PORT (int): The port the server's monitoring API uses.
        MONITORING_URL (str): URL endpoint for fetching monitoring statistics.
    """

    CONF = {
        "page_title": "TCP Server Dashboard",
        "page_icon": None,
        "layout": "wide",
        "initial_sidebar_state": "collapsed",
        "menu_items": None,
    }

    HOST = os.getenv("SERVER_HOST", "localhost")
    PORT = int(os.getenv("SERVER_PORT", "8081"))

    MONITORING_URL = f"http://{HOST}:{PORT}/monitor"

    def __init__(self):
        """
        Initializes the Dashboard application.

        - Sets the Streamlit page configuration.
        - Initializes the monitoring history DataFrame in the session state if it does not exist.
        - Fetches the latest monitoring statistics and logs them.
        - Renders charts of the monitoring statistics.
        """
        st.set_page_config(**Dashboard.CONF)

        if "monitoring_hist" not in st.session_state:
            st.session_state["monitoring_hist"] = pd.DataFrame(
                columns=[
                    "queue_size",
                    "pool_size",
                    "connected_clients",
                    "active_threads",
                ]
            )

        self.log_monitoring(current_stats=self.get_monitoring())

        with st.container():
            outer_left, inner_left, inner_right, outer_right = st.columns(4)
            outer_left.plotly_chart(self.create_fig(key="connected_clients"))
            inner_left.plotly_chart(self.create_fig(key="queue_size"))
            inner_right.plotly_chart(self.create_fig(key="active_threads"))
            outer_right.plotly_chart(self.create_fig(key="pool_size"))

    def get_monitoring(self) -> Dict[str, int]:
        """
        Fetches the current monitoring statistics from the monitoring endpoint.

        Returns:
            Dict[str, int]: A dictionary containing the latest monitoring statistics.
        """
        st_autorefresh(
            interval=1000, limit=None, debounce=True, key="monitoring_autorefresh"
        )
        response = requests.get(url=Dashboard.MONITORING_URL, timeout=10)

        return response.json()

    def log_monitoring(self, current_stats: Dict[str, int]) -> None:
        """
        Logs the current monitoring statistics to the session history DataFrame.

        Args:
            current_stats (Dict[str, int]): A dictionary containing the current monitoring
                statistics to be appended to the monitoring history.
        """
        st.session_state["monitoring_hist"].loc[
            len(st.session_state["monitoring_hist"])
        ] = current_stats

    def create_fig(self, key: str) -> Figure:
        """
        Creates a bar chart for the specified monitoring statistic.

        Args:
            key (str): The key of the monitoring statistic to visualize.

        Returns:
            Figure: A Plotly Figure object representing the bar chart for the selected statistic.
        """
        fig = px.line(
            data_frame=st.session_state["monitoring_hist"].tail(100),
            x=range(len(st.session_state["monitoring_hist"].tail(100))),
            y=st.session_state["monitoring_hist"][key].tail(100),
        )

        return fig


if __name__ == "__main__":
    Dashboard()
