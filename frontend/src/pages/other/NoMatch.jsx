import React, {useEffect, useState} from 'react';
import {Link} from "react-router-dom";

export const NoMatch = () => {
    return (
        <div>
        <h2>Nothing to see here!</h2>
        <p>
          <Link to="/">Go to the login page</Link>
        </p>
      </div>  
    );
}